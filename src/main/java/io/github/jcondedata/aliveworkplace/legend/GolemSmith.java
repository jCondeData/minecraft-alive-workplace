package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.Map;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Golem Smith (ROADMAP 29.15), a Legendary Legend {@code legends/golem_smith.json}: a Master Tinkerer inspired in a
 * happy village with four iron golems (29.10), whose Masterwork is a heavy core, "The Heart of &lt;name&gt;".
 * <p>
 * Their power {@link GolemForgePower} ({@code golem_forge}): on the hall's morning round, from the chests by the
 * smithing table near their workstation, they build one golem every {@link GolemForgePower#days} days while the village
 * has fewer than one forged golem per {@link GolemForgePower#villagersPerGolem} villagers; the costs are taken from
 * those chests. A sneak-right-click with an empty hand chooses which golem comes next ({@link Forge#next}). Each forged
 * golem is an iron golem with a role ({@link ModAttachments#GOLEM_ROLE}) and its name over its head. They also mend
 * golems {@link GolemForgePower#mendFactor} times as fast as a Tinkerer ({@link #mendFactor}).
 */
public final class GolemSmith {
	public static final ResourceLocation ID = AliveWorkplace.id("golem_smith");
	/** How far from their workstation the smithing table may stand, and the chests are looked for round it. */
	public static final int TABLE_RANGE = 6;
	public static final String TAG = "aliveworkplace_forged";

	/** The golems a Golem Smith builds, their role id and cost. */
	public enum Role {
		HAULER("hauler", Items.CHEST),
		FARMHAND("farmhand", Items.IRON_HOE),
		SENTRY("sentry", Items.SHIELD);

		public final String id;
		public final Item extra;

		Role(String id, Item extra) {
			this.id = id;
			this.extra = extra;
		}

		/** Four iron blocks, a carved pumpkin and the role's own item. */
		public Map<Item, Integer> cost() {
			return Map.of(Items.IRON_BLOCK, 4, Items.CARVED_PUMPKIN, 1, extra, 1);
		}

		public Component title() {
			return Component.translatable("entity.aliveworkplace.golem." + id);
		}

		@Nullable
		public static Role of(@Nullable String id) {
			for (Role r : values()) {
				if (r.id.equals(id)) {
					return r;
				}
			}
			return null;
		}
	}

	/** The roles the forge offers, in the order a sneak-right-click goes through them. */
	public static final List<Role> OFFERED = List.of(Role.HAULER, Role.FARMHAND, Role.SENTRY);
	/** A Wall Sentry's health, against a golem's 100. */
	public static final double SENTRY_HEALTH = 200.0;
	/** The hall lists at most this many forged golems, one line each. */
	public static final int HALL_LINES = 6;

	/** S2C: {@code entityId} is a forged golem with {@code role} (its texture drawn over the golem's). */
	public record Look(int entityId, String role) implements CustomPacketPayload {
		public static final Type<Look> TYPE = new Type<>(AliveWorkplace.id("golem_look"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Look::entityId,
			ByteBufCodecs.STRING_UTF8, Look::role,
			Look::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The texture drawn over a forged golem: {@code textures/entity/iron_golem/<role>.png}. */
	public static ResourceLocation texture(String role) {
		return AliveWorkplace.id("textures/entity/iron_golem/" + role + ".png");
	}

	static void init() {
		Platform.get().clientbound(Look.TYPE, Look.CODEC);
		Platform.get().onStartTracking((entity, player) -> {
			Role role = entity instanceof IronGolem golem ? role(golem) : null;
			if (role != null && Platform.get().canSend(player, Look.TYPE)) {
				Platform.get().send(player, new Look(entity.getId(), role.id));
			}
		});
		// A Patrol Map sneak-right-clicked on a Wall Sentry gives it its post. The client doesn't know the golem's role,
		// so it passes and the server answers.
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof IronGolem golem)
				|| !player.getItemInHand(hand).is(ModItems.PATROL_MAP) || role(golem) != Role.SENTRY) {
				return InteractionResult.PASS;
			}
			return WallSentries.giveMap((ServerPlayer) player, golem, player.getItemInHand(hand));
		});
	}

	/** A Smith's forge: which golem comes next, and the day the last was built (-1: never). */
	public record Forge(String next, long lastDay) {
		public static final Forge NEW = new Forge(Role.HAULER.id, -1L);
		public static final Codec<Forge> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("next", Role.HAULER.id).forGetter(Forge::next),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Forge::lastDay)
		).apply(i, Forge::new));

		public Role role() {
			Role r = Role.of(next);
			return r == null || !OFFERED.contains(r) ? OFFERED.get(0) : r;
		}
	}

	public static Optional<GolemForgePower> power(Villager villager) {
		if (!Legends.ENABLED) {
			return Optional.empty();
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled() || data.onStrike()) {
			return Optional.empty();
		}
		return Legends.get(data.id()).flatMap(l -> l.powers(GolemForgePower.class).stream().findFirst());
	}

	/** Whether {@code villager} is a settled Golem Smith (on strike or not: they still answer). */
	public static boolean isSmith(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return Legends.ENABLED && data != null && data.settled()
			&& Legends.get(data.id()).map(l -> !l.powers(GolemForgePower.class).isEmpty()).orElse(false);
	}

	/** How many times as fast {@code tinkerer} mends golems (1 for anyone but a Golem Smith at work). */
	public static float mendFactor(Villager tinkerer) {
		return power(tinkerer).map(GolemForgePower::mendFactor).orElse(1f);
	}

	public static Forge forge(Villager smith) {
		return ModAttachments.GOLEM_FORGE.getOrElse(smith, Forge.NEW);
	}

	/** A golem's role, or null for a plain golem. */
	@Nullable
	public static Role role(IronGolem golem) {
		return Role.of(ModAttachments.GOLEM_ROLE.get(golem));
	}

	/** A sneak-right-click with an empty hand: the next golem in {@link #OFFERED} comes next (in a protected village, only its people choose). */
	public static void choose(ServerPlayer player, Villager smith) {
		if (!VillageProtection.mayChange(player.level(), player, smith.blockPosition())) {
			return;
		}
		Forge forge = forge(smith);
		int at = OFFERED.indexOf(forge.role());
		Role next = OFFERED.get((at + 1) % OFFERED.size());
		ModAttachments.GOLEM_FORGE.set(smith, new Forge(next.id, forge.lastDay()));
		Chat.system(player, Component.translatable("message.aliveworkplace.golem_smith.next", smith.getDisplayName(), next.title(),
			costText(next)));
	}

	static Component costText(Role role) {
		return Component.translatable("message.aliveworkplace.golem_smith.cost", role.extra.getDescription());
	}

	/** The hall's round: each Golem Smith of the village builds their golem when it's time, the cap allows and the chests hold it. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return;
		}
		for (Villager smith : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && power(v).isPresent())) {
			forgeOne(level, hall, smith);
		}
	}

	/** Why {@code smith} can't build now ({@code null}: they can), and builds it if they can. Returns the golem, or null. */
	@Nullable
	public static IronGolem forgeOne(ServerLevel level, BlockPos hall, Villager smith) {
		GolemForgePower power = power(smith).orElse(null);
		if (power == null) {
			return null;
		}
		Forge forge = forge(smith);
		long today = Chronicle.day(level);
		if (forge.lastDay() >= 0 && today - forge.lastDay() < power.days()) {
			return null;
		}
		int villagers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive).size();
		if (forged(level, hall) >= villagers / power.villagersPerGolem()) {
			return null;
		}
		List<BlockPos> chests = chests(level, smith);
		Role role = forge.role();
		for (Map.Entry<Item, Integer> e : role.cost().entrySet()) {
			if (SupplyContainers.count(level, chests, e.getKey()) < e.getValue()) {
				return null;
			}
		}
		for (Map.Entry<Item, Integer> e : role.cost().entrySet()) {
			SupplyContainers.extract(level, chests, e.getKey(), e.getValue());
		}
		IronGolem golem = build(level, smith.blockPosition(), role);
		if (golem == null) {
			return null;
		}
		ModAttachments.GOLEM_FORGE.set(smith, new Forge(forge.next(), today));
		Chronicle.record(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.golem_smith.built",
			Component.literal(smith.getName().getString()), role.title()));
		return golem;
	}

	/** The golems of the village round {@code hall} built by a Golem Smith. */
	public static int forged(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), g -> g.isAlive() && role(g) != null).size();
	}

	/** The chests round the smithing table near {@code smith}'s workstation (or round the workstation, without a table). */
	public static List<BlockPos> chests(ServerLevel level, Villager smith) {
		BlockPos bench = Builders.benchPos(smith).orElse(null);
		if (bench == null) {
			return List.of();
		}
		BlockPos table = table(level, bench);
		return SupplyContainers.find(level, table != null ? table : bench, null);
	}

	@Nullable
	static BlockPos table(ServerLevel level, BlockPos bench) {
		if (level.getBlockState(bench).is(Blocks.SMITHING_TABLE)) {
			return bench;
		}
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(bench.offset(-TABLE_RANGE, -2, -TABLE_RANGE), bench.offset(TABLE_RANGE, 2, TABLE_RANGE))) {
			if (level.getBlockState(p).is(Blocks.SMITHING_TABLE)) {
				found.add(p.immutable());
			}
		}
		return found.stream().min((a, b) -> Double.compare(a.distSqr(bench), b.distSqr(bench))).orElse(null);
	}

	/** An iron golem with {@code role}, its name over its head, beside {@code near}. */
	@Nullable
	public static IronGolem build(ServerLevel level, BlockPos near, Role role) {
		IronGolem golem = EntityType.IRON_GOLEM.create(level);
		if (golem == null) {
			return null;
		}
		BlockPos spot = spot(level, golem, near);
		golem.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		golem.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null);
		golem.setPlayerCreated(true);
		golem.addTag(TAG);
		if (role == Role.SENTRY) {
			AttributeInstance health = golem.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(SENTRY_HEALTH);
				golem.setHealth(golem.getMaxHealth());
			}
		}
		golem.setCustomName(role.title().copy().withStyle(ChatFormatting.GOLD));
		golem.setCustomNameVisible(true);
		ModAttachments.GOLEM_ROLE.set(golem, role.id);
		level.addFreshEntityWithPassengers(golem);
		level.sendParticles(ParticleTypes.LAVA, golem.getX(), golem.getY() + 1, golem.getZ(), 12, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, golem.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1f, 0.8f);
		return golem;
	}

	/**
	 * Where a new golem stands: the nearest spot round {@code near} (beside the smith, at their feet's height or a step up
	 * or down) with solid ground under it and room for a golem; the open sky above {@code near} only if there is none
	 * (never on a roof when the smith works indoors).
	 */
	static BlockPos spot(ServerLevel level, IronGolem golem, BlockPos near) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 2, 3))) {
			double d = p.distSqr(near.offset(1, 0, 1));
			if (d >= bestDistance || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
				continue;
			}
			if (level.noCollision(golem, golem.getType().getDimensions().makeBoundingBox(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
				best = p.immutable();
				bestDistance = d;
			}
		}
		return best != null ? best : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.offset(1, 0, 1));
	}

	/** The hall's lines for the village's forged golems: each one's name and what it is doing. */
	public static List<Component> hallLines(ServerLevel level, BlockPos hall) {
		List<IronGolem> golems = new ArrayList<>(level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), g -> g.isAlive() && role(g) != null));
		golems.sort(Comparator.comparingInt((IronGolem g) -> role(g).ordinal()).thenComparingInt(IronGolem::getId));
		List<Component> out = new ArrayList<>();
		for (IronGolem golem : golems) {
			if (out.size() == HALL_LINES) {
				out.add(Component.translatable("screen.aliveworkplace.hall.golems_more", golems.size() - HALL_LINES));
				break;
			}
			out.add(Component.translatable("screen.aliveworkplace.hall.golem_line", role(golem).title(), doing(golem)));
		}
		return out;
	}

	/** What a forged golem is doing, for the hall. */
	static Component doing(IronGolem golem) {
		if (golem.getTarget() != null) {
			return Component.translatable("screen.aliveworkplace.hall.golem.fighting");
		}
		int carried = HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
		return switch (role(golem)) {
			case HAULER -> carried > 0 ? Component.translatable("screen.aliveworkplace.hall.golem.hauling", carried)
				: Component.translatable("screen.aliveworkplace.hall.golem.waiting");
			case FARMHAND -> carried > 0 ? Component.translatable("screen.aliveworkplace.hall.golem.harvesting", carried)
				: Component.translatable("screen.aliveworkplace.hall.golem.tending");
			case SENTRY -> {
				BlockPos post = ModAttachments.GOLEM_POST.get(golem);
				yield post == null ? Component.translatable("screen.aliveworkplace.hall.golem.no_post")
					: Component.translatable("screen.aliveworkplace.hall.golem.holding", post.getX(), post.getY(), post.getZ());
			}
		};
	}

	private GolemSmith() {
	}
}
