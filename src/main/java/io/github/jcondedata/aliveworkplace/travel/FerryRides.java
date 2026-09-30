package io.github.jcondedata.aliveworkplace.travel;

import io.github.jcondedata.aliveworkplace.mc.Boats;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The ride a Travel Ticket gives: the player sits in a boat where they used it — rowed off by the post's ferryman when
 * they're about (in front, at the oars) — for {@link #RIDE_TICKS} ticks; the view fades out and they come to on the far
 * side, next to the destination post, with a splash of oars. Getting out of the boat early (sneaking, a hit) lands
 * them straight away; so does leaving the game mid-ride. A ride boat found after a restart is taken away.
 */
public final class FerryRides {
	/** How long the ride lasts before arriving. */
	public static final int RIDE_TICKS = 60;
	/** When the view starts to fade. */
	static final int FADE_AT = 35;
	/** How far a ferryman rows the boat each tick (on water). */
	static final double ROW_SPEED = 0.1;
	/** How near a ferryman must be to row. */
	static final double FERRYMAN_RANGE = 10;
	/** Marks ride boats, so one left behind by a restart can be taken away. */
	static final String BOAT_TAG = "aliveworkplace_ferry_boat";

	private static final class Ride {
		final ServerPlayer player;
		final Entity boat;
		@Nullable
		final Villager ferryman;
		@Nullable
		final Vec3 ferrymanHome;
		final ServerLevel to;
		final BlockPos landing;
		final TravelNetwork.Post destination;
		final Vec3 heading;
		int ticks;

		Ride(ServerPlayer player, Entity boat, @Nullable Villager ferryman, @Nullable Vec3 ferrymanHome, ServerLevel to, BlockPos landing,
			TravelNetwork.Post destination, Vec3 heading) {
			this.player = player;
			this.boat = boat;
			this.ferryman = ferryman;
			this.ferrymanHome = ferrymanHome;
			this.to = to;
			this.landing = landing;
			this.destination = destination;
			this.heading = heading;
		}
	}

	private static final Map<UUID, Ride> RIDES = new LinkedHashMap<>();

	public static void init() {
		Platform.get().onServerTick(server -> tick());
		Platform.get().onPlayerLeave(player -> {
			Ride ride = RIDES.remove(player.getUUID());
			if (ride != null) {
				end(ride, true);
			}
		});
		Platform.get().onEntityLoad((entity, level) -> {
			if (entity.getTags().contains(BOAT_TAG) && RIDES.values().stream().noneMatch(r -> r.boat == entity)) {
				entity.discard(); // (left behind when the server stopped mid-ride)
			}
		});
	}

	/** Whether {@code player} is on a ferry ride now. */
	public static boolean riding(ServerPlayer player) {
		return RIDES.containsKey(player.getUUID());
	}

	/**
	 * Sets off for {@code destination}, landing at {@code landing} in {@code to}: a boat ride first, or straight there for a
	 * player who can't sit in a boat just now (already riding something, a spectator, already on a ride).
	 */
	static void start(ServerPlayer player, ServerLevel to, BlockPos landing, TravelNetwork.Post destination) {
		ServerLevel here = (ServerLevel) player.level();
		if (riding(player) || player.isPassenger() || player.isSpectator() || player.isSleeping()) {
			arrive(player, to, landing, destination);
			return;
		}
		Vec3 at = player.position();
		BlockPos top = player.blockPosition();
		if (here.getFluidState(top).is(FluidTags.WATER)) {
			// Standing or swimming in water: the boat goes on top of it, not under
			while (here.getFluidState(top.above()).is(FluidTags.WATER)) {
				top = top.above();
			}
			at = new Vec3(at.x, top.getY() + 0.9, at.z);
		}
		Vec3 heading = here == to ? Vec3.atCenterOf(landing).subtract(at) : player.getLookAngle();
		heading = new Vec3(heading.x, 0, heading.z);
		heading = heading.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : heading.normalize();
		Entity boat = Boats.launch(here, at, Boats.yaw(at, at.add(heading)), new ItemStack(Items.SPRUCE_BOAT));
		boat.addTag(BOAT_TAG);
		Villager ferryman = ferrymanNear(here, player);
		Vec3 home = null;
		if (ferryman != null) {
			home = ferryman.position();
			ferryman.startRiding(boat, true);
		}
		player.startRiding(boat, true);
		if (ferryman != null) {
			Boats.atOars(boat, ferryman);
		}
		here.playSound(null, player.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.travel.setting_off", destination.name()).withStyle(ChatFormatting.GRAY));
		RIDES.put(player.getUUID(), new Ride(player, boat, ferryman, home, to, landing, destination, heading));
	}

	@Nullable
	private static Villager ferrymanNear(ServerLevel level, ServerPlayer player) {
		return level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(FERRYMAN_RANGE),
				v -> Ferrymen.isFerryman(v) && v.isAlive() && !v.isSleeping() && !v.isPassenger())
			.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElse(null);
	}

	private static void tick() {
		if (RIDES.isEmpty()) {
			return;
		}
		for (Ride ride : new ArrayList<>(RIDES.values())) {
			ride.ticks++;
			boolean done = ride.ticks >= RIDE_TICKS || !ride.player.isAlive() || ride.player.hasDisconnected()
				|| ride.boat.isRemoved() || ride.player.getVehicle() != ride.boat;
			if (done) {
				RIDES.remove(ride.player.getUUID());
				end(ride, ride.player.isAlive());
				continue;
			}
			ServerLevel level = (ServerLevel) ride.boat.level();
			if (ride.ferryman != null && ride.ferryman.getVehicle() == ride.boat) {
				// Eyes on where he's going (not twisted round to the passenger)
				ride.ferryman.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
					new net.minecraft.world.entity.ai.behavior.BlockPosTracker(BlockPos.containing(ride.boat.position().add(ride.heading.scale(8))).above()));
				if (ride.boat.isInWater()) {
					Boats.row(ride.boat, ride.boat.position().add(ride.heading.scale(4)), ROW_SPEED);
				} else {
					Boats.row(ride.boat, ride.boat.position(), 0); // (on land the oars just go round)
				}
			}
			if (ride.ticks % 10 == 0) {
				level.playSound(null, ride.boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
				level.sendParticles(ParticleTypes.SPLASH, ride.boat.getX(), ride.boat.getY() + 0.2, ride.boat.getZ(), 6, 0.6, 0, 0.6, 0);
			}
			if (ride.ticks == FADE_AT) {
				ride.player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RIDE_TICKS - FADE_AT + 30, 0, false, false, false));
			}
		}
	}

	/** The end of a ride: the ferryman back on the jetty, the boat gone, and (if {@code go}) the player over there. */
	private static void end(Ride ride, boolean go) {
		if (ride.ferryman != null && ride.ferryman.getVehicle() == ride.boat) {
			ride.ferryman.stopRiding();
			if (ride.ferrymanHome != null) {
				ride.ferryman.teleportTo(ride.ferrymanHome.x, ride.ferrymanHome.y, ride.ferrymanHome.z);
			}
		}
		if (ride.player.getVehicle() == ride.boat) {
			ride.player.stopRiding();
		}
		ride.boat.discard();
		if (go) {
			arrive(ride.player, ride.to, ride.landing, ride.destination);
		}
	}

	/** Lands {@code player} by the destination post. */
	static void arrive(ServerPlayer player, ServerLevel to, BlockPos spot, TravelNetwork.Post destination) {
		player.teleportTo(to, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
		to.playSound(null, spot, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1f, 0.9f);
		to.sendParticles(ParticleTypes.SPLASH, spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 12, 0.6, 0, 0.6, 0);
		TravelNetwork.get(to.getServer()).visit(player.getUUID(), destination);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.travel.arrived", destination.name()).withStyle(ChatFormatting.GREEN));
	}

	private FerryRides() {
	}
}
