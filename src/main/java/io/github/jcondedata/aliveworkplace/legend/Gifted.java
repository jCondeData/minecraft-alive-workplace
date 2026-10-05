package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/**
 * Gifted villagers (M29, 29.6): about one villager in {@link #CHANCE} has a rare gift as well as their traits. Gifts are
 * data ({@code data/<ns>/gifted/<id>.json}: a weight and toolbox effects, {@link GiftPowers}). The gift comes from the
 * villager's UUID, as {@code Traits.of} rolls traits but with other salts, so every existing villager has theirs with
 * nothing to migrate; the attachment {@code GIFTED} (a gift's id, or {@code none}) overrides the roll for the born and
 * the inn's travellers (29.7). {@code giftedChance} 0 turns gifts off (nothing is erased).
 */
public final class Gifted implements ResourceManagerReloadListener {
	public static final String FOLDER = "gifted";
	public static final String NONE = "none";
	/** One villager in this many is Gifted (config {@code giftedChance}; 0: nobody). */
	public static volatile int CHANCE = 30;
	/** Whether the UUID roll decides (off in GameTests, so no test villager turns up with a gift by chance). */
	public static volatile boolean ROLL = System.getProperty("fabric-api.gametest") == null;

	private static final long CHANCE_SALT = 0x6A09E667F3BCC909L;
	private static final long PICK_SALT = 0xBB67AE8584CAA73BL;

	/** One gift, read from its file. */
	public record Gift(ResourceLocation id, String name, String description, int weight, List<Power> effects) {
		public Component title() {
			return Component.translatable(name);
		}

		public Component describe() {
			return Component.translatable(description);
		}

		public boolean has(String type) {
			return effects.stream().anyMatch(p -> p.type().equals(type));
		}

		@Nullable
		public <P extends Power> P effect(Class<P> kind) {
			for (Power p : effects) {
				if (kind.isInstance(p)) {
					return kind.cast(p);
				}
			}
			return null;
		}
	}

	private static Map<ResourceLocation, Gift> gifts = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id("gifted"), new Gifted());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, Gift> out = new LinkedHashMap<>();
		List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet());
		files.sort(Map.Entry.comparingByKey());
		for (Map.Entry<ResourceLocation, Resource> e : files) {
			ResourceLocation file = e.getKey();
			String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
			try (Reader reader = e.getValue().openAsReader()) {
				out.put(id, read(id, JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping gift {}: {}", file, ex.getMessage());
			}
		}
		gifts = java.util.Collections.unmodifiableMap(out);
		AliveWorkplace.LOG.info("Gifts: {}", gifts.keySet());
	}

	/** Reads one gift file; throws for a bad one (an unknown effect type, no weight). */
	public static Gift read(ResourceLocation id, JsonObject json) {
		int weight = json.has("weight") ? json.get("weight").getAsInt() : 0;
		if (weight <= 0) {
			throw new IllegalArgumentException("'weight' must be above 0");
		}
		String name = json.has("name") ? json.get("name").getAsString() : "gifted." + id.getNamespace() + "." + id.getPath();
		String description = json.has("description") ? json.get("description").getAsString() : name + ".desc";
		return new Gift(id, name, description, weight, Powers.parseAll(json.getAsJsonArray("effects")));
	}

	public static Map<ResourceLocation, Gift> all() {
		return gifts;
	}

	@Nullable
	public static Gift get(ResourceLocation id) {
		return gifts.get(id);
	}

	/** {@code villager}'s gift, or null: the {@code GIFTED} attachment if it has one, else the UUID roll. */
	@Nullable
	public static Gift of(Villager villager) {
		if (CHANCE <= 0 || gifts.isEmpty()) {
			return null;
		}
		String set = ModAttachments.GIFTED.getOrElse(villager, null);
		if (set != null) {
			ResourceLocation id = NONE.equals(set) ? null : ResourceLocation.tryParse(set);
			return id == null ? null : gifts.get(id);
		}
		return ROLL ? roll(villager.getUUID()) : null;
	}

	/**
	 * The gift someone with this UUID is born with, or null. Whether they're Gifted is a threshold on one hash (so
	 * changing {@link #CHANCE} only adds or removes people at the edge); which gift is a weighted pick from another.
	 */
	@Nullable
	public static Gift roll(UUID id) {
		if (CHANCE <= 0 || gifts.isEmpty() || !isGifted(id, CHANCE)) {
			return null;
		}
		int total = 0;
		for (Gift g : gifts.values()) {
			total += g.weight();
		}
		long pick = Math.floorMod(mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 29) ^ PICK_SALT), (long) total);
		for (Gift g : gifts.values()) {
			pick -= g.weight();
			if (pick < 0) {
				return g;
			}
		}
		return null;
	}

	/** Whether a UUID falls under a 1-in-{@code chance} threshold. */
	public static boolean isGifted(UUID id, int chance) {
		if (chance <= 0) {
			return false;
		}
		long h = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 13) ^ CHANCE_SALT);
		double u = (h >>> 11) * 0x1.0p-53;
		return u < 1.0 / chance;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Gives {@code villager} this gift (or none, with null), whatever the roll says; their schedule follows. */
	public static void set(Villager villager, @Nullable ResourceLocation gift) {
		ModAttachments.GIFTED.set(villager, gift == null ? NONE : gift.toString());
		if (villager.level() instanceof ServerLevel level) {
			villager.refreshBrain(level);
		}
	}

	/**
	 * In the hall's round: a villager Gifted by the roll has their gift written down, so a gift file added later (which
	 * reshuffles the weighted pick) never swaps it for another (design decision 2). Nothing is written for the ungifted.
	 */
	public static void keep(Villager villager) {
		if (CHANCE > 0 && ROLL && !ModAttachments.GIFTED.has(villager)) {
			Gift gift = roll(villager.getUUID());
			if (gift != null) {
				ModAttachments.GIFTED.set(villager, gift.id().toString());
			}
		}
	}

	public static boolean has(Villager villager, String type) {
		Gift gift = of(villager);
		return gift != null && gift.has(type);
	}

	/** How many times as fast {@code villager} learns from their gift (1: no gift for it). */
	public static float xpFactor(Villager villager) {
		Gift gift = of(villager);
		GiftPowers.Xp xp = gift == null ? null : gift.effect(GiftPowers.Xp.class);
		return xp == null ? 1f : xp.factor();
	}

	/** Iron Will: never panics, keeps working through raids and the bell. */
	public static boolean noPanic(Villager villager) {
		return has(villager, "no_panic");
	}

	/** Night Owl: works dusk to dawn. */
	public static boolean nightShift(Villager villager) {
		return has(villager, "night_shift");
	}

	/** Silver Tongue's discount in percent (0: none). */
	public static int tradeDiscount(Villager villager) {
		Gift gift = of(villager);
		GiftPowers.TradeDiscount d = gift == null ? null : gift.effect(GiftPowers.TradeDiscount.class);
		return d == null ? 0 : d.percent();
	}

	/** Takes Silver Tongue's discount off every offer (after vanilla set the player's special prices; at least 1). */
	public static void discount(Villager villager) {
		int percent = tradeDiscount(villager);
		if (percent <= 0) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int off = (int) Math.floor(percent / 100.0 * offer.getBaseCostA().getCount());
			offer.addToSpecialPriceDiff(-Math.max(off, 1));
		}
	}

	/** Whether the Night Owl schedule applies: a grown villager with a trade and the night shift. */
	public static boolean nightOwl(Villager villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		return !villager.isBaby() && p != VillagerProfession.NONE && p != VillagerProfession.NITWIT && nightShift(villager);
	}

	/** Now and then: a Night Owl whose brain isn't on the night schedule (a gift set after loading, a config change) is put on it. */
	public static void tick(Villager villager) {
		if ((villager.tickCount + villager.getId()) % 100 != 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		boolean owl = nightOwl(villager);
		boolean onIt = villager.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE;
		if (owl != onIt) {
			villager.refreshBrain(level);
		}
	}

	/** A Gifted villager sparkles when they level up. */
	public static void onLevelUp(Villager villager) {
		if (of(villager) != null && villager.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.2, villager.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
			level.playSound(null, villager.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
		}
	}

	/** The gold line on the hall's list: "Gifted: Prodigy (learns three times as fast)"; null without a gift. */
	@Nullable
	public static Component hallLine(Villager villager) {
		Gift gift = of(villager);
		return gift == null ? null : Component.translatable("screen.aliveworkplace.hall.gifted", gift.title(), gift.describe());
	}

	private Gifted() {
	}
}
