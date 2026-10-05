package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Tonics (ROADMAP 30.15, docs/design/M30.md): drinks that make a villager of the jobs they suit work faster for a while.
 * They are data, {@code data/<namespace>/tonics/<id>.json}: the item (any item can be one), its maker ({@code alchemist},
 * the vanilla Cleric's {@code AlchemistWork}, or {@code chef}, {@code ChefWork}), its ingredients (items or
 * {@code #tags}, repeated for more than one), the jobs it suits, its effects ({@link CivicEffects}, {@code work_pace}
 * read by {@code work/Pace} as the {@code tonic} source), how long it lasts and how many its maker keeps stocked. A pack
 * switches one of ours off with {@code "enabled": false}.
 *
 * <p>A player gives one with a plain right-click on a villager (sneak-right-click stays the job gesture): one that suits
 * their job is drunk, and the villager's {@code tonic} attachment holds its id and the game time it wears off; another
 * tonic starts that time again and replaces it, so they never stack. One that doesn't suit them is refused and kept;
 * an item that is a tonic only by a pack's file, not our own tonic item, goes on to its usual use instead. Players
 * can't craft them. {@code tonics} in the config: off, makers don't make them, villagers refuse them and a tonic drunk
 * earlier does nothing (it stays saved).
 */
public final class Tonics implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("tonics");
	public static final String FOLDER = "tonics";
	/** {@code tonics} in the config. */
	public static boolean ENABLED = true;
	/** Ticks in one minute, for the status line and the tooltip. */
	private static final int MINUTE = 1200;

	/** Who makes a tonic. */
	public enum Maker {
		/** The vanilla Cleric at their brewing stand, after the guards' potions. */
		ALCHEMIST,
		/** The Chef at their stove, after the menu. */
		CHEF;

		public static final Codec<Maker> CODEC = Codec.STRING.comapFlatMap(s -> {
			for (Maker m : values()) {
				if (m.name().toLowerCase(Locale.ROOT).equals(s)) {
					return DataResult.success(m);
				}
			}
			return DataResult.error(() -> "unknown maker " + s + " (alchemist or chef)");
		}, m -> m.name().toLowerCase(Locale.ROOT));
	}

	/** One ingredient: an item, or any item of a tag ({@code "#minecraft:small_flowers"}). */
	public record Ingredient(@Nullable Item item, @Nullable TagKey<Item> tag) {
		public static final Codec<Ingredient> CODEC = Codec.STRING.comapFlatMap(s -> {
			if (s.startsWith("#")) {
				ResourceLocation id = ResourceLocation.tryParse(s.substring(1));
				return id == null ? DataResult.error(() -> "bad tag " + s) : DataResult.success(new Ingredient(null, TagKey.create(Registries.ITEM, id)));
			}
			ResourceLocation id = ResourceLocation.tryParse(s);
			Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
			return item.isEmpty() ? DataResult.error(() -> "unknown item " + s) : DataResult.success(new Ingredient(item.get(), null));
		}, Ingredient::toString);

		public boolean matches(Item other) {
			return item != null ? other == item : new ItemStack(other).is(tag);
		}

		/** "Coal", or a tag's name ("#minecraft:small_flowers" when it has none). */
		public Component name() {
			if (item != null) {
				return item.getDescription();
			}
			ResourceLocation id = tag.location();
			return Component.translatableWithFallback("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'), "#" + id);
		}

		@Override
		public String toString() {
			return item != null ? BuiltInRegistries.ITEM.getKey(item).toString() : "#" + tag.location();
		}
	}

	/** One tonic, as its file says. */
	public record Tonic(ResourceLocation id, Item item, Maker maker, List<Ingredient> ingredients, List<ResourceLocation> jobs,
						List<CivicEffects.Effect> effects, int ticks, int keep) {
		/** Whether it suits {@code villager}: a grown villager of one of its jobs. */
		public boolean suits(Villager villager) {
			return !villager.isBaby() && jobs.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
		}

		/** The work time its {@code work_pace} effects make for {@code villager} (1: none reach them). */
		public float factor(Villager villager) {
			float f = 1f;
			for (CivicEffects.Effect effect : effects) {
				if (effect instanceof CivicEffects.WorkPace pace && CivicEffects.reaches(effect, villager)) {
					f *= pace.factor();
				}
			}
			return f;
		}

		/** How much faster its {@code work_pace} effects make work, in percent (those narrowed to some jobs included). */
		public int percent() {
			float f = 1f;
			for (CivicEffects.Effect effect : effects) {
				if (effect instanceof CivicEffects.WorkPace pace) {
					f *= pace.factor();
				}
			}
			return Math.round((1f / f - 1f) * 100f);
		}

		/** Its item's name: "Miner's Brew". */
		public Component name() {
			return new ItemStack(item).getHoverName();
		}
	}

	/** The tonic a villager drank and the game time it wears off. Saved on the villager ({@code tonic}). */
	public record Drunk(String id, long until) {
		public static final Codec<Drunk> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Drunk::id),
			Codec.LONG.fieldOf("until").forGetter(Drunk::until)
		).apply(i, Drunk::new));
	}

	private record Body(Item item, Maker maker, List<Ingredient> ingredients, List<ResourceLocation> jobs, List<CivicEffects.Effect> effects,
						int ticks, int keep) {
	}

	private static final MapCodec<Body> BODY = RecordCodecBuilder.mapCodec(i -> i.group(
		BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Body::item),
		Maker.CODEC.fieldOf("maker").forGetter(Body::maker),
		Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(Body::ingredients),
		ResourceLocation.CODEC.listOf().fieldOf("jobs").forGetter(Body::jobs),
		CivicEffects.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Body::effects),
		Codec.intRange(1, 24000 * 30).optionalFieldOf("ticks", 24000).forGetter(Body::ticks),
		Codec.intRange(0, 64).optionalFieldOf("keep", 4).forGetter(Body::keep)
	).apply(i, Body::new));

	private static volatile Map<ResourceLocation, Tonic> tonics = Map.of();
	private static volatile Map<Item, Tonic> byItem = Map.of();
	/** Bumped whenever the tonics change, so the players' tooltips are sent again. */
	private static volatile int generation;
	private static int sent = -1;
	/** On a client: each tonic item's tooltip lines, as the server sent them. */
	private static volatile Map<ResourceLocation, List<Component>> clientLines = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new Tonics());
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> use(player, level, hand, entity));
		Platform.get().clientbound(Sync.TYPE, Sync.CODEC);
		Platform.get().onPlayerJoin(Tonics::send);
		Platform.get().onServerTick(server -> {
			if (sent != generation) {
				sent = generation;
				server.getPlayerList().getPlayers().forEach(Tonics::send);
			}
		});
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads every tonic file (the top one per path); a broken file is skipped with a warning. */
	public static void load(Map<ResourceLocation, Resource> files) {
		List<Tonic> found = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				Tonic tonic = read(id, JsonParser.parseReader(reader));
				if (tonic != null) {
					found.add(tonic);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping tonic {}: {}", e.getKey(), ex.getMessage());
			}
		}
		set(found);
	}

	/** Reads one tonic; null if it is switched off ({@code "enabled": false}); throws naming the bad field. */
	@Nullable
	public static Tonic read(ResourceLocation id, JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		if (object.has("enabled") && !object.get("enabled").getAsBoolean()) {
			return null;
		}
		Body body = BODY.codec().parse(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new);
		if (body.ingredients().isEmpty()) {
			throw new IllegalArgumentException("no ingredients");
		}
		return new Tonic(id, body.item(), body.maker(), body.ingredients(), body.jobs(), body.effects(), body.ticks(), body.keep());
	}

	/** The tonics there are from now on (in id order; of two for the same item the later id wins, with a warning). */
	public static void set(List<Tonic> found) {
		found = new ArrayList<>(found);
		found.sort(Comparator.comparing(t -> t.id().toString()));
		Map<ResourceLocation, Tonic> out = new LinkedHashMap<>();
		Map<Item, Tonic> items = new HashMap<>();
		for (Tonic t : found) {
			Tonic before = items.put(t.item(), t);
			if (before != null) {
				out.remove(before.id());
				AliveWorkplace.LOG.warn("Tonics {} and {} are both {}: {} is used", before.id(), t.id(), t.item(), t.id());
			}
			out.put(t.id(), t);
		}
		tonics = Collections.unmodifiableMap(out);
		byItem = Map.copyOf(items);
		generation++;
	}

	/** Every tonic, in id order. */
	public static List<Tonic> all() {
		return List.copyOf(tonics.values());
	}

	public static Optional<Tonic> get(ResourceLocation id) {
		return Optional.ofNullable(tonics.get(id));
	}

	/** The tonic {@code stack} is, if it's one. */
	@Nullable
	public static Tonic of(ItemStack stack) {
		return stack.isEmpty() ? null : byItem.get(stack.getItem());
	}

	// --- Drinking ------------------------------------------------------------------------------------------------------

	/** What came of offering a tonic. */
	public enum Outcome { DRUNK, REFUSED, DISABLED, NOT_A_TONIC }

	/** What came of offering a tonic, and what the player was told (null: nothing). */
	public record Offer(Outcome outcome, @Nullable Component message) {
	}

	/** The player's plain right-click on a villager with a tonic in the main hand. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return held.getItem() instanceof TonicItem ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		Outcome outcome = offer((ServerPlayer) player, villager, held).outcome();
		return outcome == Outcome.NOT_A_TONIC ? InteractionResult.PASS : InteractionResult.SUCCESS;
	}

	/**
	 * {@code player} offers {@code stack} to {@code villager}: drunk (one used up, unless in creative) when it suits
	 * them, else refused and kept with a sentence; an item that is a tonic only by a pack's file and doesn't suit them
	 * is left to its usual use ({@link Outcome#NOT_A_TONIC}).
	 */
	public static Offer offer(ServerPlayer player, Villager villager, ItemStack stack) {
		Tonic tonic = of(stack);
		boolean ours = stack.getItem() instanceof TonicItem;
		Offer offer;
		if (tonic == null || !ENABLED || !tonic.suits(villager)) {
			if (!ours) {
				return new Offer(Outcome.NOT_A_TONIC, null);
			}
			Component name = stack.getHoverName();
			if (!ENABLED) {
				offer = new Offer(Outcome.DISABLED, Component.translatable("message.aliveworkplace.tonic.disabled", villager.getDisplayName(), name)
					.withStyle(ChatFormatting.RED));
			} else {
				offer = new Offer(Outcome.REFUSED, Component.translatable("message.aliveworkplace.tonic.no_use", villager.getDisplayName(), name)
					.withStyle(ChatFormatting.RED));
				villager.setUnhappyCounter(40);
				villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
			}
		} else {
			drink((ServerLevel) villager.level(), villager, tonic);
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			offer = new Offer(Outcome.DRUNK, Component.translatable("message.aliveworkplace.tonic.drunk", villager.getDisplayName(), tonic.name(),
				percentFor(tonic, villager), Math.max(1, tonic.ticks() / MINUTE)).withStyle(ChatFormatting.GREEN));
		}
		Chat.actionBar(player, offer.message());
		return offer;
	}

	/** {@code villager} drinks {@code tonic}: its time starts now, replacing any tonic drunk before. */
	public static void drink(ServerLevel level, Villager villager, Tonic tonic) {
		ModAttachments.TONIC.set(villager, new Drunk(tonic.id().toString(), level.getGameTime() + tonic.ticks()));
		level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
	}

	private static int percentFor(Tonic tonic, Villager villager) {
		return Math.round((1f / tonic.factor(villager) - 1f) * 100f);
	}

	/** The tonic working in {@code villager} now, if any (none with tonics off, worn off, or no longer loaded). */
	@Nullable
	public static Tonic active(Villager villager) {
		if (!ENABLED) {
			return null;
		}
		Drunk drunk = ModAttachments.TONIC.get(villager);
		if (drunk == null || villager.level().getGameTime() >= drunk.until()) {
			return null;
		}
		ResourceLocation id = ResourceLocation.tryParse(drunk.id());
		return id == null ? null : tonics.get(id);
	}

	/** Game ticks left of {@code villager}'s tonic (0: none working). */
	public static long ticksLeft(Villager villager) {
		Drunk drunk = ModAttachments.TONIC.get(villager);
		return active(villager) == null ? 0 : drunk.until() - villager.level().getGameTime();
	}

	/** The work time {@code villager}'s tonic makes (a source of {@code work/Pace}; 1: none). */
	public static float pace(Villager villager) {
		Tonic tonic = active(villager);
		return tonic == null ? 1f : tonic.factor(villager);
	}

	/** "Miner's Brew, 19 min left" (the {@code tonic} source in the status line). */
	public static Component paceLabel(Villager villager) {
		Tonic tonic = active(villager);
		Component name = tonic == null ? Component.translatable("pace.aliveworkplace.source.tonic.none") : tonic.name();
		return Component.translatable("pace.aliveworkplace.source.tonic", name, Math.max(1, ticksLeft(villager) / MINUTE));
	}

	// --- Making --------------------------------------------------------------------------------------------------------

	/** {@code count} of {@code tonic} to make, and the items that takes. */
	public record Order(Tonic tonic, int count, Map<Item, Integer> takes) {
	}

	/**
	 * The items {@code count} of {@code tonic} take out of {@code stock} (a tag's ingredient from any of its items, several
	 * if need be), or null if the stock is short.
	 */
	@Nullable
	public static Map<Item, Integer> takes(Tonic tonic, int count, Map<Item, Long> stock) {
		Map<Item, Long> left = new LinkedHashMap<>(stock);
		Map<Item, Integer> takes = new LinkedHashMap<>();
		for (Ingredient in : tonic.ingredients()) {
			int need = count;
			for (Map.Entry<Item, Long> e : left.entrySet()) {
				if (need <= 0) {
					break;
				}
				if (e.getValue() <= 0 || !in.matches(e.getKey())) {
					continue;
				}
				int take = (int) Math.min(need, e.getValue());
				e.setValue(e.getValue() - take);
				takes.merge(e.getKey(), take, Integer::sum);
				need -= take;
			}
			if (need > 0) {
				return null;
			}
		}
		return takes;
	}

	/**
	 * The next tonic {@code maker} should make: the first (in id order) with fewer than its {@code keep} in {@code own},
	 * as many as {@code sources} have the makings for, up to {@code most} and the keep. Null with tonics off or nothing
	 * to make.
	 */
	@Nullable
	public static Order next(ServerLevel level, Maker maker, List<BlockPos> own, List<BlockPos> sources, int most) {
		if (!ENABLED || own.isEmpty()) {
			return null;
		}
		Map<Item, Long> stock = null;
		for (Tonic tonic : tonics.values()) {
			if (tonic.maker() != maker || tonic.keep() <= 0) {
				continue;
			}
			long have = SupplyContainers.count(level, own, tonic.item());
			if (have >= tonic.keep()) {
				continue;
			}
			if (stock == null) {
				stock = SupplyContainers.contents(level, sources);
			}
			for (int n = (int) Math.min(most, tonic.keep() - have); n >= 1; n--) {
				Map<Item, Integer> takes = takes(tonic, n, stock);
				if (takes != null) {
					return new Order(tonic, n, takes);
				}
			}
		}
		return null;
	}

	/** Where a maker takes the makings from: the chests by their station, then the village store's (the porters'). */
	public static List<BlockPos> sources(ServerLevel level, Villager maker, BlockPos station, List<BlockPos> own) {
		List<BlockPos> out = new ArrayList<>(own);
		for (Village.Stash stash : Village.stashes(level, maker, station, null)) {
			if (stash.job() == ModVillagers.PORTER) {
				for (BlockPos chest : stash.chests()) {
					if (!out.contains(chest)) {
						out.add(chest);
					}
				}
			}
		}
		return out;
	}

	// --- Tooltips ------------------------------------------------------------------------------------------------------

	/**
	 * What a tonic's tooltip says: what it does, for which jobs, who makes it from what, and how it's given.
	 * "Drink: 25% faster work for 20 minutes" / "For Miners, Sifters and Netherworkers" / "Brewed by a Cleric from Glass
	 * Bottle, Glowstone Dust, Coal and Sugar" / "Right-click a villager it suits; players can't make it".
	 */
	public static List<Component> tooltip(Tonic tonic) {
		List<Component> lines = new ArrayList<>();
		int percent = tonic.percent();
		if (percent != 0) {
			lines.add(Component.translatable("tooltip.aliveworkplace.tonic.effect", percent, Math.max(1, tonic.ticks() / MINUTE))
				.withStyle(ChatFormatting.BLUE));
		}
		List<Component> jobs = new ArrayList<>();
		for (ResourceLocation job : tonic.jobs()) {
			jobs.add(jobName(job));
		}
		lines.add(Component.translatable("tooltip.aliveworkplace.tonic.jobs", join(jobs)).withStyle(ChatFormatting.GRAY));
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Ingredient> kinds = new LinkedHashMap<>();
		for (Ingredient in : tonic.ingredients()) {
			counts.merge(in.toString(), 1, Integer::sum);
			kinds.putIfAbsent(in.toString(), in);
		}
		List<Component> makings = new ArrayList<>();
		kinds.forEach((key, in) -> makings.add(counts.get(key) == 1 ? in.name() : many(in, counts.get(key))));
		lines.add(Component.translatable("tooltip.aliveworkplace.tonic.maker." + tonic.maker().name().toLowerCase(Locale.ROOT), join(makings))
			.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.aliveworkplace.tonic.how").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/**
	 * {@code count} of an ingredient: "2 Iron Nuggets" when the lang has the item's plural
	 * ({@code tonic.aliveworkplace.many.<namespace>.<path>}), else "2 Sweet Berries" (the count and the item's name).
	 */
	static Component many(Ingredient in, int count) {
		if (in.item() != null) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(in.item());
			return Component.translatableWithFallback("tonic.aliveworkplace.many." + id.getNamespace() + "." + id.getPath(), "%s %s", count, in.name());
		}
		return Component.translatable("tooltip.aliveworkplace.tonic.ingredient_count", count, in.name());
	}

	/** A job's name for many of them: "Miners", "Dyers" (a lang key per job, or the job's id made plural). */
	public static Component jobName(ResourceLocation job) {
		String path = job.getPath();
		String fallback = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ') + "s";
		return Component.translatableWithFallback("tonic.aliveworkplace.jobs." + job.getNamespace() + "." + path, fallback);
	}

	/** "A", "A and B", "A, B and C". */
	static Component join(List<Component> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(i == parts.size() - 1 ? Component.translatable("tooltip.aliveworkplace.tonic.and") : Component.literal(", "));
			}
			out.append(parts.get(i));
		}
		return out;
	}

	/** The tooltip lines of the item {@code item} on a client (empty when it isn't a tonic there). */
	public static List<Component> clientTooltip(ResourceLocation item) {
		return clientLines.getOrDefault(item, List.of());
	}

	/** On a client: the server's tonics' tooltip lines arrived. */
	public static void receive(Sync sync) {
		Map<ResourceLocation, List<Component>> out = new HashMap<>();
		sync.entries().forEach(e -> out.put(e.item(), e.lines()));
		clientLines = Map.copyOf(out);
	}

	/** S2C: every tonic item's tooltip lines (the client has no tonic files of its own). */
	public record Sync(List<Entry> entries) implements CustomPacketPayload {
		public record Entry(ResourceLocation item, List<Component> lines) {
			static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, Entry::item,
				ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Entry::lines,
				Entry::new);
		}

		public static final Type<Sync> TYPE = new Type<>(AliveWorkplace.id("tonics"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = Entry.CODEC.apply(ByteBufCodecs.list(512)).map(Sync::new, Sync::entries);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The tooltips as they are now, for the players. */
	public static Sync sync() {
		List<Sync.Entry> entries = new ArrayList<>();
		for (Tonic tonic : tonics.values()) {
			entries.add(new Sync.Entry(BuiltInRegistries.ITEM.getKey(tonic.item()), tooltip(tonic)));
		}
		return new Sync(entries);
	}

	private static void send(ServerPlayer player) {
		if (Platform.get().canSend(player, Sync.TYPE)) {
			Platform.get().send(player, sync());
		}
	}

	private Tonics() {
	}
}
