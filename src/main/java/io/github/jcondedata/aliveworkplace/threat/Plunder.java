package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The tactic {@code plunder} (ROADMAP 32.10, the piglins): every {@link #EVERY} ticks a raider of a culture that names
 * it (not a ram), carrying nothing yet, that is within {@link #REACH} blocks of a chest (any storage block) in the
 * village takes the gold in it: gold ingots, nuggets, blocks, raw gold and golden gear ({@link #isGold}), one stack at
 * most, kept on the raider ({@code ModAttachments.PLUNDER}, saved with it) and shown on its head when that is bare.
 * <ul>
 * <li>Killed, it drops what it carried where it fell.</li>
 * <li>At the end of its hours (dawn) it carries it home: into a chest of its lair when one stands and is loaded, and
 * the village has lost it. The hall keeps the count ({@link ThreatData#plundered}); the siege report says it the morning
 * after, or the chronicle when there was no siege.</li>
 * </ul>
 */
public final class Plunder {
	public static final String TACTIC = "plunder";
	/** How near a chest a raider must come to plunder it. */
	public static final int REACH = 4;
	/** How often (ticks) the raiders look about them for chests. */
	public static final int EVERY = 20;
	/** The tag of a raider showing its plunder on its head (the head is ours to clear again). */
	public static final String SHOWN_TAG = "aliveworkplace_plunder_shown";
	private static final Set<Item> GOLD = Set.of(Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK, Items.RAW_GOLD, Items.RAW_GOLD_BLOCK);

	public static void init() {
		Threats.registerTactic(TACTIC, new Threats.Tactic() {
			@Override
			public void leave(ServerLevel level, BlockPos hall, Culture culture, Mob raider) {
				homeward(level, hall, raider);
			}

			@Override
			public void end(ServerLevel level, BlockPos hall, Culture culture, boolean fled) {
				ThreatData data = ThreatData.get(level);
				int lost = data.plundered(hall);
				// With a siege the morning report tells it; without one the chronicle does now.
				if (lost > 0 && data.siege(hall).isEmpty()) {
					Chronicle.atHall(level, hall, Chronicle.Kind.RAID, Component.translatable(culture.chronicle().key("plunder", "chronicle.aliveworkplace.plunder"), lost));
					data.forgetPlunder(hall);
				}
			}
		});
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == 0) {
				tick(level);
			}
		});
		Platform.get().afterDeath((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				dropOnDeath(level, entity);
			}
		});
	}

	/** Whether {@code stack} is gold a plunderer takes: ingots, nuggets, blocks, raw gold, and golden gear (tools, weapons, armour). */
	public static boolean isGold(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (GOLD.contains(stack.getItem())) {
			return true;
		}
		return stack.is(ItemTags.PIGLIN_LOVED) && stack.isDamageableItem() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("golden_");
	}

	/** What {@code raider} carries off (empty: nothing). */
	public static ItemStack carried(Mob raider) {
		ItemStack stack = ModAttachments.PLUNDER.get(raider);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	private static void tick(ServerLevel level) {
		ThreatData data = ThreatData.get(level);
		for (ThreatData.Under raid : data.raids()) {
			Optional<Culture> culture = Threats.get(raid.culture());
			if (culture.isEmpty() || !culture.get().tactics().contains(TACTIC)) {
				continue;
			}
			AABB village = VillageHalls.area(raid.hall());
			for (Mob raider : Sieges.raiders(level, raid.hall())) {
				if (Threats.role(raider) != Culture.Role.RAM && carried(raider).isEmpty() && village.contains(raider.position())) {
					plunder(level, raider, village);
				}
			}
		}
	}

	/** {@code raider} looks for a chest within {@link #REACH} blocks in the village and takes its gold; true if it did. */
	public static boolean plunder(ServerLevel level, Mob raider, AABB village) {
		BlockPos at = raider.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-REACH, -2, -REACH), at.offset(REACH, 3, REACH))) {
			if (p.distSqr(at) > REACH * REACH + 4 || !village.contains(p.getCenter())) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			if (!state.hasBlockEntity()) {
				continue;
			}
			BlockEntity entity = level.getBlockEntity(p);
			if (entity == null || !Platform.get().items().isStore(level, p, state, entity)) {
				continue;
			}
			List<ItemStack> taken = Platform.get().items().takeMatching(level, p.immutable(), Plunder::isGold, 1);
			if (!taken.isEmpty() && !taken.get(0).isEmpty()) {
				carry(level, raider, taken.get(0));
				level.playSound(null, p, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.9f);
				level.playSound(null, raider.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.HOSTILE, 1f, 1f);
				return true;
			}
		}
		return false;
	}

	/** {@code raider} carries {@code stack} off; shown on its head when that is bare. */
	public static void carry(ServerLevel level, Mob raider, ItemStack stack) {
		ModAttachments.PLUNDER.set(raider, stack.copy());
		if (raider.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			raider.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));
			raider.setDropChance(EquipmentSlot.HEAD, 0f);
			raider.addTag(SHOWN_TAG);
		}
	}

	/** Takes the plunder off {@code raider} (and off its head); returns it. */
	static ItemStack takeOff(Mob raider) {
		ItemStack stack = carried(raider);
		ModAttachments.PLUNDER.remove(raider);
		if (raider.removeTag(SHOWN_TAG)) {
			raider.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
		return stack;
	}

	/** A plunderer died: what it carried falls where it did. */
	static void dropOnDeath(ServerLevel level, LivingEntity entity) {
		if (!(entity instanceof Mob mob) || carried(mob).isEmpty()) {
			return;
		}
		ItemStack stack = takeOff(mob);
		mob.spawnAtLocation(stack, 0.5f);
	}

	/**
	 * Dawn: {@code raider} goes home with what it carried. It goes into a chest of the lair its raid came from (when that
	 * stands and is loaded), and the village round {@code hall} counts it lost.
	 */
	static void homeward(ServerLevel level, BlockPos hall, Mob raider) {
		ItemStack stack = takeOff(raider);
		if (stack.isEmpty()) {
			return;
		}
		ThreatData.get(level).plunder(hall, stack.getCount());
		level.sendParticles(ParticleTypes.PORTAL, raider.getX(), raider.getY() + 1, raider.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		Lairs.near(level, hall).filter(l -> level.isLoaded(l.pos())).ifPresent(lair -> {
			BlockPos hoard = hoard(level, lair);
			if (hoard != null) {
				Platform.get().items().insert(level, List.of(hoard), stack);
			}
		});
	}

	/** A chest of {@code lair}: the storage block nearest its middle, within its ground; null if it has none. */
	@Nullable
	static BlockPos hoard(ServerLevel level, Lairs.Lair lair) {
		BlockPos best = null;
		BlockPos c = lair.pos();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -4, -14), c.offset(14, 8, 14))) {
			BlockState state = level.getBlockState(p);
			if (state.hasBlockEntity() && Platform.get().items().isStore(level, p, state, level.getBlockEntity(p))
				&& (best == null || p.distSqr(c) < best.distSqr(c))) {
				best = p.immutable();
			}
		}
		return best;
	}

	private Plunder() {
	}
}
