package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * {@code weddings} (35.10, the Great Cathedral's): while the Wonder stands, its village's weddings are held at its altar
 * ({@code altar}, a spot in the Wonder's own blueprint, before the Chapel or the bell: {@code people/Couples.venue}) and
 * are <b>blessed</b>: the whole village is {@code mood} happier for {@code days} days ({@link VillageBlessing}, on the
 * hall), the couple's first baby comes the next day there's a free bed, without the store's meals or the daily wait
 * ({@link Blessed}, on each of them; {@code hall/VillageGrowth}), and their children are {@code gifted_factor} times as
 * likely to grow up Gifted ({@code legend/BornGifts}, M29's born Legends). With {@code bells} (on unless the file says
 * no) its bells ring for weddings, festivals and raids ({@link WonderBells}).
 */
public record WeddingsPower(int mood, int days, double giftedFactor, Optional<BlockPos> altar, boolean bells) implements WonderPower {
	public static final String TYPE = "weddings";
	/** Most a blessed wedding may add to the village's mood (a mood runs 0 to 100). */
	public static final int MAX_MOOD = 50;
	/** Most times as likely to grow up Gifted a blessed couple's child may be. */
	public static final double MAX_GIFTED = 4.0;

	static WeddingsPower read(JsonObject json) {
		int mood = json.has("mood") ? json.get("mood").getAsInt() : 15;
		int days = json.has("days") ? json.get("days").getAsInt() : 3;
		double gifted = json.has("gifted_factor") ? json.get("gifted_factor").getAsDouble() : 1.0;
		if (Math.abs(mood) > MAX_MOOD) {
			throw new IllegalArgumentException("'mood' must be -" + MAX_MOOD + " to " + MAX_MOOD);
		}
		if (days < 0) {
			throw new IllegalArgumentException("'days' below 0");
		}
		if (gifted < 1.0 || gifted > MAX_GIFTED) {
			throw new IllegalArgumentException("'gifted_factor' must be 1 to " + MAX_GIFTED + ", not " + gifted);
		}
		Optional<BlockPos> altar = Optional.empty();
		if (json.has("altar")) {
			JsonArray a = json.getAsJsonArray("altar");
			if (a.size() != 3) {
				throw new IllegalArgumentException("'altar' must be [x, y, z]");
			}
			altar = Optional.of(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
		}
		boolean bells = !json.has("bells") || json.get("bells").getAsBoolean();
		return new WeddingsPower(mood, days, gifted, altar, bells);
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** "Weddings held here are blessed: everyone in the village is 15 happier for 3 days, and a baby comes soon". */
	@Override
	public Component describe() {
		return Component.translatable("wonder.aliveworkplace.power.weddings", mood, days);
	}

	/** A blessed wedding, on each of the couple (35.10): the day, whether the first baby is still to come, and the Gifted factor for their children. */
	public record Blessed(long day, boolean babyDue, double giftedFactor) {
		public static final Codec<Blessed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Blessed::day),
			Codec.BOOL.optionalFieldOf("baby_due", false).forGetter(Blessed::babyDue),
			Codec.DOUBLE.optionalFieldOf("gifted_factor", 1.0).forGetter(Blessed::giftedFactor)
		).apply(i, Blessed::new));
	}

	/** The village's blessed wedding (35.10), on the hall: the day, how much happier everyone is and for how many days, at which Wonder. */
	public record VillageBlessing(long day, int mood, int days, ResourceLocation wonder) {
		public static final Codec<VillageBlessing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(VillageBlessing::day),
			Codec.INT.optionalFieldOf("mood", 15).forGetter(VillageBlessing::mood),
			Codec.INT.optionalFieldOf("days", 3).forGetter(VillageBlessing::days),
			ResourceLocation.CODEC.fieldOf("wonder").forGetter(VillageBlessing::wonder)
		).apply(i, VillageBlessing::new));
	}

	/** The power reaching the village round {@code hall} with the Wonder granting it, while it's finished and standing. */
	public static Optional<Wonders.Granted> granted(ServerLevel level, BlockPos hall) {
		return Wonders.powers(level, hall, TYPE).stream().filter(g -> g.home() && g.power() instanceof WeddingsPower).findFirst();
	}

	/** Where the village's weddings are held while its Wonder stands: before the altar of its own blueprint, placed as the Wonder was. */
	public static Optional<BlockPos> venue(ServerLevel level, BlockPos hall) {
		return granted(level, hall).flatMap(WeddingsPower::altarOf);
	}

	/** The altar of the Wonder {@code g}: its placement's origin plus the power's {@code altar}, turned and mirrored as the Wonder is. */
	public static Optional<BlockPos> altarOf(Wonders.Granted g) {
		if (!(g.power() instanceof WeddingsPower w) || w.altar().isEmpty() || g.site().placement().isEmpty()) {
			return Optional.empty();
		}
		var at = g.site().placement().get();
		return Optional.of(at.origin().offset(StructureTemplate.transform(w.altar().get(), at.mirror(), at.rotation(), BlockPos.ZERO)));
	}

	/**
	 * {@code a} and {@code b} were wed at {@code venue}: blessed if the village's Wonder with the power stands and the
	 * wedding was at its altar (or it has none). The village gets its mood, the couple their baby to come; the chronicle and
	 * the players near say so. Returns whether it was.
	 */
	public static boolean bless(ServerLevel level, BlockPos hall, Villager a, Villager b, BlockPos venue) {
		Optional<Wonders.Granted> g = granted(level, hall);
		if (g.isEmpty() || !(g.get().power() instanceof WeddingsPower power) || altarOf(g.get()).map(p -> !p.equals(venue)).orElse(false)
			|| !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return false;
		}
		long today = Chronicle.day(level);
		entity.setWonderWedding(new VillageBlessing(today, power.mood(), power.days(), g.get().wonder().id()));
		Blessed blessed = new Blessed(today, true, power.giftedFactor());
		for (Villager v : List.of(a, b)) {
			ModAttachments.WONDER_BLESSING.set(v, blessed);
			level.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY() + 1.2, v.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
		}
		io.github.jcondedata.aliveworkplace.people.Moods.forget(); // everyone's mood with the blessing in it
		level.playSound(null, venue, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2f, 1.2f);
		Component wonder = g.get().wonder().nameText();
		Component village = VillageHalls.name(level, hall);
		double r = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.wonder.wedding_blessed", a.getDisplayName(), b.getDisplayName(), wonder,
				village, power.mood(), power.days()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.wonder_wedding", a.getDisplayName(),
			b.getDisplayName(), wonder));
		return true;
	}

	/** The village's mood from its last blessed wedding, while it lasts ("a blessed wedding at the Great Cathedral"); null after. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isEmpty() || !(level.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity)) {
			return null;
		}
		VillageBlessing b = entity.wonderWedding();
		if (b == null || b.mood() == 0 || Chronicle.day(level) - b.day() >= b.days()) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.wonder_wedding", Wonders.name(b.wonder())), b.mood());
	}

	/** A blessed couple of the village round {@code hall} whose first baby is due (from the day after the wedding, both there), or empty. */
	public static List<Villager> blessedCouple(ServerLevel level, BlockPos hall) {
		long today = Chronicle.day(level);
		List<Villager> village = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby());
		for (Villager v : village) {
			Blessed b = ModAttachments.WONDER_BLESSING.get(v);
			Couples.Partner p = Couples.partner(v);
			if (b == null || !b.babyDue() || today <= b.day() || p == null || !p.married()) {
				continue;
			}
			if (level.getEntity(p.id()) instanceof Villager other && village.contains(other)) {
				return List.of(v, other);
			}
		}
		return List.of();
	}

	/** The blessed couple's first baby came: the next ones come as anyone's do. */
	public static void babyCame(List<Villager> parents) {
		for (Villager v : parents) {
			Blessed b = ModAttachments.WONDER_BLESSING.get(v);
			if (b != null) {
				ModAttachments.WONDER_BLESSING.set(v, new Blessed(b.day(), false, b.giftedFactor()));
			}
		}
	}

	/** {@code baby} was born to {@code parents}: a blessed couple's child keeps the Gifted factor until they grow up. */
	public static void born(Villager baby, List<Villager> parents) {
		double factor = 1.0;
		for (Villager v : parents) {
			Blessed b = ModAttachments.WONDER_BLESSING.get(v);
			if (b != null) {
				factor = Math.max(factor, b.giftedFactor());
			}
		}
		if (factor > 1.0) {
			ModAttachments.BLESSED_CHILD.set(baby, factor);
		}
	}

	/** How many times as likely {@code villager}, grown up, is to be Gifted: their parents' blessing's factor, else 1. */
	public static double giftedFactor(Villager villager) {
		return ModAttachments.BLESSED_CHILD.getOrElse(villager, 1.0);
	}
}
