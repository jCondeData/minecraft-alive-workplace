package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What players can do to Pokémon with an item in hand — milk a Miltank with a bucket, brush a Pidgeotto for feathers,
 * bone-meal a Cacnea for cactus — done by the village's herder to the Pokémon in Pasture Blocks near their smoker. The
 * chores come from Cobblemon's own data ({@code data/<ns>/pokemon_interactions/*.json}, read when data packs load, so
 * data packs that add more work too): which Pokémon, the item it takes (and how much of it — brushes wear), what comes
 * out and how long before the same Pokémon can be done again (at least {@link #MIN_COOLDOWN} for a villager, who would
 * otherwise never stop brushing). Only the chores with plain results are done (not the ones that run a script, like
 * trimming a Furfrou). Nothing is read or done without Cobblemon.
 */
public final class PokemonChores implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("pokemon_chores");
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	/** The least time between two of the same chore on one Pokémon. */
	public static long MIN_COOLDOWN = 2400;

	/** Something that comes out: an item, between min and max of it. */
	public record Output(Item item, int min, int max) {
	}

	/**
	 * One chore: done to Pokémon matching {@code target} (and {@code extra}, when the file splits one species by form), with
	 * one of the items {@code takes}, using up {@code uses} of it (durability for a tool).
	 */
	public record Chore(String target, @Nullable String extra, String grouping, long cooldown, String takes, int uses, List<Output> outputs,
						@Nullable ResourceLocation sound) {
		public boolean accepts(ItemStack stack) {
			if (takes.startsWith("#")) {
				ResourceLocation tag = ResourceLocation.tryParse(takes.substring(1));
				return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
			}
			ResourceLocation item = ResourceLocation.tryParse(takes);
			return item != null && stack.is(Lookup.value(BuiltInRegistries.ITEM, item));
		}
	}

	/** A chore for a particular Pokémon. */
	public record Job(Entity pokemon, Chore chore) {
	}

	private static List<Chore> chores = List.of();
	private static final Map<UUID, Map<String, Long>> LAST_DONE = new HashMap<>();

	public static void init() {
		if (COBBLEMON) {
			Platform.get().onDataReload(ID, new PokemonChores());
		}
	}

	public static List<Chore> chores() {
		return chores;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Chore> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("pokemon_interactions", p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				read(JsonParser.parseReader(reader).getAsJsonObject(), out);
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Skipping Pokémon interaction {}: {}", e.getKey(), ex.toString());
			}
		}
		chores = List.copyOf(out);
		AliveWorkplace.LOG.info("Herders know {} Pokémon chores", chores.size());
	}

	/** Reads one interactions file (Cobblemon's format) into chores. */
	static void read(JsonObject file, List<Chore> out) {
		String target = null;
		for (JsonElement r : file.getAsJsonArray("requirements")) {
			JsonObject req = r.getAsJsonObject();
			if ("properties".equals(req.get("variant").getAsString())) {
				target = req.get("target").getAsString();
			}
		}
		if (target == null) {
			return;
		}
		for (JsonElement i : file.getAsJsonArray("interactions")) {
			JsonObject interaction = i.getAsJsonObject();
			String takes = null;
			String extra = null;
			boolean understood = true;
			for (JsonElement r : interaction.has("requirements") ? interaction.getAsJsonArray("requirements") : new com.google.gson.JsonArray()) {
				JsonObject req = r.getAsJsonObject();
				switch (req.get("variant").getAsString()) {
					case "owner_held_item" -> takes = req.get("itemCondition").getAsString();
					case "properties" -> extra = req.get("target").getAsString();
					default -> understood = false;
				}
			}
			int uses = 1;
			List<Output> outputs = new ArrayList<>();
			ResourceLocation sound = null;
			for (JsonElement ef : interaction.getAsJsonArray("effects")) {
				JsonObject effect = ef.getAsJsonObject();
				switch (effect.get("variant").getAsString()) {
					case "shrink_item" -> uses = effect.has("amount") ? effect.get("amount").getAsInt() : 1;
					case "give_item", "drop_item" -> {
						Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(effect.get("item").getAsString())).orElse(null);
						if (item == null) {
							understood = false;
						} else {
							int[] range = range(effect.has("amount") ? effect.get("amount").getAsString() : "1");
							outputs.add(new Output(item, range[0], range[1]));
						}
					}
					case "play_sound" -> sound = ResourceLocation.tryParse(effect.get("sound").getAsString());
					default -> understood = false; // a script: not something a villager can do
				}
			}
			if (understood && takes != null && !outputs.isEmpty()) {
				long cooldown = interaction.has("cooldown") ? Long.parseLong(interaction.get("cooldown").getAsString().trim()) : 0;
				String grouping = interaction.has("grouping") ? interaction.get("grouping").getAsString() : takes;
				out.add(new Chore(target, extra, grouping, cooldown, takes, uses, List.copyOf(outputs), sound));
			}
		}
	}

	/** "2" → 2..2, "1-3" → 1..3. */
	static int[] range(String amount) {
		String[] parts = amount.trim().split("-");
		int min = Integer.parseInt(parts[0].trim());
		int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
		return new int[] {min, Math.max(min, max)};
	}

	/**
	 * The nearest pastured Pokémon within {@code radius} of {@code station} with a chore that's due and whose item is in
	 * {@code own} chests or the bag; null if there's none.
	 */
	@Nullable
	public static Job next(ServerLevel level, BlockPos station, int radius, List<BlockPos> own, BuilderBag bag) {
		if (!COBBLEMON || chores.isEmpty()) {
			return null;
		}
		long now = level.getGameTime();
		Vec3 middle = Vec3.atCenterOf(station);
		List<Entity> pastured = new ArrayList<>(PokemonPartners.EXTENSION.call(p -> p.pastured(level, station, radius), List.<Entity>of()));
		pastured.sort(Comparator.comparingDouble(e -> e.distanceToSqr(middle)));
		for (Entity pokemon : pastured) {
			if (airborne(pokemon)) {
				continue; // a Pidgey flying about can't be brushed: it's done once it lands (B24)
			}
			for (Chore chore : chores) {
				if (!due(pokemon, chore, now) || !matches(pokemon, chore.target()) || chore.extra() != null && !matches(pokemon, chore.extra())) {
					continue;
				}
				if (bag.stacks().stream().anyMatch(chore::accepts) || SupplyContainers.firstMatching(level, own, chore::accepts) != null) {
					return new Job(pokemon, chore);
				}
			}
		}
		return null;
	}

	/** Whether a Pokémon is up in the air (flying, not just hopping): out of a villager's reach until it comes down. */
	public static boolean airborne(Entity pokemon) {
		return !pokemon.onGround() && !pokemon.isInWater() && !pokemon.isPassenger()
			&& pokemon.level().getBlockState(pokemon.blockPosition().below()).isAir()
			&& pokemon.level().getBlockState(pokemon.blockPosition().below(2)).isAir();
	}

	private static boolean matches(Entity pokemon, String properties) {
		return PokemonPartners.EXTENSION.call(p -> p.matches(pokemon, properties), false);
	}

	private static boolean due(Entity pokemon, Chore chore, long now) {
		Long last = LAST_DONE.getOrDefault(pokemon.getUUID(), Map.of()).get(chore.grouping());
		return last == null || now - last >= Math.max(MIN_COOLDOWN, chore.cooldown());
	}

	/** Fetches what the chore takes from the chests into the bag: true once it's there, false if there's none. */
	public static boolean fetch(ServerLevel level, Job job, List<BlockPos> own, BuilderBag bag) {
		if (bag.stacks().stream().anyMatch(job.chore()::accepts)) {
			return true;
		}
		ItemStack taken = SupplyContainers.takeOne(level, own, job.chore()::accepts);
		if (taken.isEmpty()) {
			return false;
		}
		ItemStack rest = bag.add(taken);
		return rest.isEmpty();
	}

	/** Does the chore (the Pokémon is in reach): the item used up (a tool worn), what comes out into the bag. */
	public static boolean perform(ServerLevel level, Job job, BuilderBag bag) {
		Chore chore = job.chore();
		Predicate<ItemStack> accepts = chore::accepts;
		ItemStack used = bag.takeFirst(accepts);
		if (used.isEmpty()) {
			return false;
		}
		if (used.isDamageableItem()) {
			used.setDamageValue(used.getDamageValue() + chore.uses());
			if (used.getDamageValue() >= used.getMaxDamage()) {
				used = ItemStack.EMPTY;
				level.playSound(null, job.pokemon().blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.8f, 1f);
			}
		} else {
			used.shrink(chore.uses());
		}
		if (!used.isEmpty()) {
			bag.add(used);
		}
		for (Output output : chore.outputs()) {
			int count = output.min() + (output.max() > output.min() ? level.random.nextInt(output.max() - output.min() + 1) : 0);
			if (count > 0) {
				ItemStack rest = bag.add(new ItemStack(output.item(), count));
				if (!rest.isEmpty()) {
					net.minecraft.world.level.block.Block.popResource(level, job.pokemon().blockPosition(), rest);
				}
			}
		}
		if (chore.sound() != null) {
			BuiltInRegistries.SOUND_EVENT.getOptional(chore.sound())
				.ifPresent(s -> level.playSound(null, job.pokemon().blockPosition(), s, SoundSource.NEUTRAL, 1f, 1f));
		}
		LAST_DONE.computeIfAbsent(job.pokemon().getUUID(), u -> new HashMap<>()).put(chore.grouping(), level.getGameTime());
		return true;
	}

	/** Forget when chores were last done (tests). */
	public static void forget() {
		LAST_DONE.clear();
	}
}
