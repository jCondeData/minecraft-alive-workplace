package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * A guard's shift (WORK): take the best weapon and armor from the chests near the Guard Post, then walk
 * the area around the post, staying close to it at night. Fighting is {@link GuardCombat}'s job.
 */
public class GuardPatrol extends Behavior<Villager> {
	private static final float SPEED = 0.5f;
	private static final int GEAR_CHECK_EVERY = 400;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private BlockPos waypoint;
	private int wait;
	private int gearTimer;
	@Nullable
	private BlockPos gearChest;

	public GuardPatrol() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		waypoint = null;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
		for (EquipmentSlot slot : ARMOR) {
			villager.setDropChance(slot, 0f);
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (GuardCombat.isFighting(villager)) {
			waypoint = null;
			return;
		}
		BlockPos post = Builders.benchPos(villager).orElse(null);
		if (post == null) {
			return;
		}
		// Better gear in the chests? Go and get it.
		if (gearChest == null && --gearTimer <= 0) {
			gearTimer = GEAR_CHECK_EVERY;
			gearChest = SupplyContainers.firstMatching(level, SupplyContainers.find(level, post, null), stack -> isUpgrade(villager, stack));
		}
		if (gearChest != null) {
			status(villager, "gearing_up");
			if (walker.walkTo(level, villager, gearChest, 3.0)) {
				equipBest(level, villager, SupplyContainers.find(level, post, null));
				level.playSound(null, villager.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8f, 1f);
				gearChest = null;
			}
			return;
		}
		// Walk the area; stay near the post at night.
		status(villager, "patrolling");
		if (wait > 0) {
			wait--;
			return;
		}
		if (waypoint == null) {
			boolean night = level.isNight();
			int r = night ? 6 : 14;
			int x = post.getX() + level.random.nextInt(r * 2 + 1) - r;
			int z = post.getZ() + level.random.nextInt(r * 2 + 1) - r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos p = new BlockPos(x, y, z);
			waypoint = Math.abs(y - post.getY()) <= 6 && Walker.canStand(level, p) ? p : null;
			walker.reset();
			return;
		}
		if (walker.walkTo(level, villager, waypoint, 2.0) || walker.noSpot()) {
			waypoint = null;
			wait = 60 + level.random.nextInt(100);
		}
	}

	private static void status(Villager villager, String state) {
		WorkerStatus.set(villager, GuardCombat.title(villager), -1f,
			Component.translatable("message.aliveworkplace.guard.state." + state).withStyle(ChatFormatting.GRAY));
	}

	/** True if {@code stack} beats what the guard has in that slot. */
	static boolean isUpgrade(Villager villager, ItemStack stack) {
		if (Guards.isWeapon(stack)) {
			return Guards.baseDamage(stack) > Guards.baseDamage(villager.getItemBySlot(EquipmentSlot.MAINHAND));
		}
		if (stack.getItem() instanceof ArmorItem armor) {
			EquipmentSlot slot = armor.getEquipmentSlot();
			return Guards.armorValue(stack) > Guards.armorValue(villager.getItemBySlot(slot));
		}
		return false;
	}

	/** Swaps in the best weapon and armor from the chests; what they had goes back in. */
	static void equipBest(ServerLevel level, Villager villager, List<BlockPos> chests) {
		// Each swap raises the bar, so a few rounds end with the best piece of each kind.
		for (int i = 0; i < 4 && take(level, villager, chests, EquipmentSlot.MAINHAND, stack -> Guards.isWeapon(stack)
			&& Guards.baseDamage(stack) > Guards.baseDamage(villager.getItemBySlot(EquipmentSlot.MAINHAND))); i++) {
		}
		for (EquipmentSlot slot : ARMOR) {
			for (int i = 0; i < 4 && take(level, villager, chests, slot, stack -> stack.getItem() instanceof ArmorItem armor
				&& armor.getEquipmentSlot() == slot && Guards.armorValue(stack) > Guards.armorValue(villager.getItemBySlot(slot))); i++) {
			}
		}
	}

	private static boolean take(ServerLevel level, Villager villager, List<BlockPos> chests, EquipmentSlot slot, Predicate<ItemStack> better) {
		ItemStack got = SupplyContainers.takeOne(level, chests, better);
		if (got.isEmpty()) {
			return false;
		}
		ItemStack old = villager.getItemBySlot(slot);
		villager.setItemSlot(slot, got);
		villager.setDropChance(slot, 0f);
		if (!old.isEmpty()) {
			ItemStack rest = SupplyContainers.insert(level, chests, old);
			if (!rest.isEmpty()) {
				villager.spawnAtLocation(rest);
			}
		}
		return true;
	}
}
