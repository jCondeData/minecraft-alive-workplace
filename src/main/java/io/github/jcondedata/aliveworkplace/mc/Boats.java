package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Boats that villagers row (fishers out to open water, ferrymen taking passengers). Boats change a lot between versions
 * (in 1.21.2 each wood became its own entity type and {@code Boat.Type} went), so everything about them is here. The
 * server moves a boat whose first passenger isn't a player by its motion alone, so rowing is setting that motion each
 * tick (the boat's water friction takes about a tenth off).
 */
public final class Boats {
	/** Whether {@code stack} is a boat a villager can row (a plain boat or raft, not one with a chest). */
	public static boolean isRowingBoat(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BoatItem && !stack.is(ItemTags.CHEST_BOATS);
	}

	/** Whether {@code entity} is a plain boat (not one with a chest). */
	public static boolean isBoat(Entity entity) {
		return entity instanceof Boat && !(entity instanceof ChestBoat);
	}

	/** Puts a boat of {@code item}'s wood (oak for boats it doesn't know) on the water at {@code pos}, facing {@code yaw}. */
	public static Entity launch(ServerLevel level, Vec3 pos, float yaw, ItemStack item) {
		Boat boat = new Boat(level, pos.x, pos.y, pos.z);
		String path = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
		boat.setVariant(Boat.Type.byName(path.replace("_boat", "").replace("_raft", "")));
		boat.setYRot(yaw);
		level.addFreshEntity(boat);
		return boat;
	}

	/** The item {@code boat} would break into. */
	public static ItemStack item(Entity boat) {
		return boat instanceof Boat b ? new ItemStack(b.getDropItem()) : ItemStack.EMPTY;
	}

	/** The yaw that faces from {@code from} to {@code to}. */
	public static float yaw(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90f;
	}

	/**
	 * One stroke of the oars: turns {@code boat} towards {@code to} and sets it moving there at up to {@code speed} blocks
	 * a tick, the paddles going. Returns the distance left (across the water, not up and down).
	 */
	public static double row(Entity boat, Vec3 to, double speed) {
		double dx = to.x - boat.getX();
		double dz = to.z - boat.getZ();
		double left = Math.sqrt(dx * dx + dz * dz);
		if (left > 1.0E-3) {
			double step = Math.min(speed, left) / left;
			if (left > 0.75) { // (close in, the heading would swing about)
				boat.setYRot(yaw(boat.position(), to));
			}
			boat.setDeltaMovement(dx * step, boat.getDeltaMovement().y, dz * step);
		}
		if (boat instanceof Boat b) {
			b.setPaddleState(true, true);
		}
		return left;
	}

	/**
	 * Seats {@code rower} first in {@code boat}, where the one who steers sits (the game puts a player first, whose own
	 * client would then steer): with a villager at the oars the server moves the boat, player and all.
	 */
	public static void atOars(Entity boat, Entity rower) {
		java.util.List<Entity> seats = new java.util.ArrayList<>(boat.getPassengers());
		if (seats.remove(rower)) {
			seats.add(0, rower);
			((io.github.jcondedata.aliveworkplace.mixin.EntityAccessor) boat).aliveworkplace$setPassengers(com.google.common.collect.ImmutableList.copyOf(seats));
		}
	}

	/** Oars in: the boat drifts to a stop. */
	public static void rest(Entity boat) {
		if (boat instanceof Boat b) {
			b.setPaddleState(false, false);
		}
	}

	private Boats() {
	}
}
