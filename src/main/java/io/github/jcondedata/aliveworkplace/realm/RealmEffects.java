package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The realm topics' own effect types (ROADMAP 33.16), added to the {@link CivicEffects} toolbox. Each is read by the one
 * system it changes, through {@link RealmResearch#effects(net.minecraft.server.MinecraftServer, RealmData.Realm, Class)}:
 * <ul>
 * <li>{@code caravan_speed} ({@code percent}): caravans between villages of the realm arrive that much sooner
 * ({@code hall/Caravans.trip});</li>
 * <li>{@code board_spread} ({@code points}): the board sells that many points nearer its buying price
 * ({@code trade/TradePage.sellPercent});</li>
 * <li>{@code noon_post}: parcels between mailboxes in the realm's villages also go out at noon ({@code mail/PostOffice.noon});</li>
 * <li>{@code ticket_discount} ({@code percent}): Travel Tickets between posts in the realm's villages cost that much less,
 * at least an emerald, for members' owners and their friends ({@code travel/TravelNetwork.fare});</li>
 * <li>{@code relief_riders} ({@code riders}): relief bands one rider bigger per village (33.17 reads
 * {@link RealmResearch#reliefRiders});</li>
 * <li>{@code colony_charter} ({@code cost_percent}, {@code supplies}): Colony Charters cost less, and the settlers bring
 * more of each supply ({@code colony/Colonies.cost}, {@code colony/Settlers.load});</li>
 * <li>{@code granary} ({@code below}, {@code meals}): at dawn a member whose store holds fewer than {@code below} meals
 * is sent up to {@code meals} by caravan from the member with the fullest store, outside the route cap
 * ({@link RealmResearch#granaries}).</li>
 * </ul>
 * Concord uses the toolbox's {@code wellbeing} (research/TreeEffects), so wellbeing's pace keeps its 25% top.
 */
public final class RealmEffects {
	public static final ResourceLocation CARAVAN_SPEED = AliveWorkplace.id("caravan_speed");
	public static final ResourceLocation BOARD_SPREAD = AliveWorkplace.id("board_spread");
	public static final ResourceLocation NOON_POST = AliveWorkplace.id("noon_post");
	public static final ResourceLocation TICKET_DISCOUNT = AliveWorkplace.id("ticket_discount");
	public static final ResourceLocation RELIEF_RIDERS = AliveWorkplace.id("relief_riders");
	public static final ResourceLocation COLONY_CHARTER = AliveWorkplace.id("colony_charter");
	public static final ResourceLocation GRANARY = AliveWorkplace.id("granary");

	private static final Codec<List<ResourceLocation>> JOBS = ResourceLocation.CODEC.listOf();
	private static boolean done;

	public static synchronized void init() {
		if (done) {
			return;
		}
		done = true;
		CivicEffects.register(CARAVAN_SPEED, RecordCodecBuilder.<CaravanSpeed>mapCodec(i -> i.group(
			Codec.intRange(0, 90).fieldOf("percent").forGetter(CaravanSpeed::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(CaravanSpeed::jobs)
		).apply(i, CaravanSpeed::new)));
		CivicEffects.register(BOARD_SPREAD, RecordCodecBuilder.<BoardSpread>mapCodec(i -> i.group(
			Codec.intRange(0, 100).fieldOf("points").forGetter(BoardSpread::points),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(BoardSpread::jobs)
		).apply(i, BoardSpread::new)));
		CivicEffects.register(NOON_POST, MapCodec.unit(new NoonPost(List.of())));
		CivicEffects.register(TICKET_DISCOUNT, RecordCodecBuilder.<TicketDiscount>mapCodec(i -> i.group(
			Codec.intRange(0, 100).fieldOf("percent").forGetter(TicketDiscount::percent),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(TicketDiscount::jobs)
		).apply(i, TicketDiscount::new)));
		CivicEffects.register(RELIEF_RIDERS, RecordCodecBuilder.<ReliefRiders>mapCodec(i -> i.group(
			Codec.intRange(0, 16).fieldOf("riders").forGetter(ReliefRiders::riders),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(ReliefRiders::jobs)
		).apply(i, ReliefRiders::new)));
		CivicEffects.register(COLONY_CHARTER, RecordCodecBuilder.<ColonyCharter>mapCodec(i -> i.group(
			Codec.intRange(0, 100).optionalFieldOf("cost_percent", 0).forGetter(ColonyCharter::costPercent),
			Codec.intRange(0, 1024).optionalFieldOf("supplies", 0).forGetter(ColonyCharter::supplies),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(ColonyCharter::jobs)
		).apply(i, ColonyCharter::new)));
		CivicEffects.register(GRANARY, RecordCodecBuilder.<Granary>mapCodec(i -> i.group(
			Codec.intRange(1, 4096).optionalFieldOf("below", 16).forGetter(Granary::below),
			Codec.intRange(1, 4096).optionalFieldOf("meals", 32).forGetter(Granary::meals),
			JOBS.optionalFieldOf("jobs", List.of()).forGetter(Granary::jobs)
		).apply(i, Granary::new)));
	}

	/** {@code caravan_speed}: caravans between the realm's villages arrive {@code percent} sooner. */
	public record CaravanSpeed(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return CARAVAN_SPEED;
		}
	}

	/** {@code board_spread}: the board's spread {@code points} narrower. */
	public record BoardSpread(int points, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return BOARD_SPREAD;
		}
	}

	/** {@code noon_post}: a second mail round at noon between the realm's villages. */
	public record NoonPost(List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return NOON_POST;
		}
	}

	/** {@code ticket_discount}: Travel Tickets between the realm's posts {@code percent} cheaper (at least an emerald). */
	public record TicketDiscount(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return TICKET_DISCOUNT;
		}
	}

	/** {@code relief_riders}: relief bands {@code riders} bigger per village (33.17). */
	public record ReliefRiders(int riders, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RELIEF_RIDERS;
		}
	}

	/** {@code colony_charter}: charters {@code cost_percent} cheaper, settlers bring {@code supplies} more of each supply. */
	public record ColonyCharter(int costPercent, int supplies, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return COLONY_CHARTER;
		}
	}

	/** {@code granary}: a member under {@code below} meals is sent up to {@code meals} at dawn. */
	public record Granary(int below, int meals, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return GRANARY;
		}
	}

	private RealmEffects() {
	}
}
