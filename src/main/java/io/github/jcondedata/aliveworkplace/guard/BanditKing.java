package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

/**
 * The Bandit King (ROADMAP 31.15): a vindicator named "Grimwald, the Bandit King" (a first name from the bounty
 * captains' list, {@code bounties/captains.json}), crowned with a golden helmet, in iron chestplate, leggings and boots,
 * with a diamond axe and {@link #HEALTH} health, sitting in his keep at the back of his fort. Players within
 * {@link #BAR_RANGE} blocks see his boss bar. At half health he blows a goat horn and {@link #CALLED} bandits come out
 * of the keep, once (a tag on him remembers it). He and his men keep to the fort ({@link BanditFort}).
 *
 * <p>His crown is the King's Crown that lies in his treasure chest (Protection III, Unbreaking III, named); he doesn't
 * drop it, nor the rest of his gear.
 */
public final class BanditKing {
	public static final String TAG = "aliveworkplace_bandit_king";
	/** On the King once he has blown his horn. */
	public static final String HORN_TAG = "aliveworkplace_bandit_king_horn";
	/** On the bandits his horn called. */
	public static final String CALLED_TAG = "aliveworkplace_bandit_king_called";
	/** Where the bandits his horn calls come out: {@code <prefix><pos as long>}. */
	static final String DOOR_PREFIX = "aliveworkplace_bandit_king_door.";
	public static final double HEALTH = 150;
	public static final int BAR_RANGE = 32;
	public static final int CALLED = 4;
	/** How often (ticks) the fort's men are looked over: the King's bar and horn, everyone's leash. */
	public static final int CHECK_EVERY = 10;
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	/** The King's Crown: a golden helmet, Protection III and Unbreaking III, named. */
	public static ItemStack crown(ServerLevel level) {
		ItemStack crown = new ItemStack(Items.GOLDEN_HELMET);
		Registry<Enchantment> registry = Lookup.registry(level.registryAccess(), Registries.ENCHANTMENT);
		for (ResourceKey<Enchantment> enchantment : List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING)) {
			Lookup.holder(registry, enchantment).ifPresent(holder -> crown.enchant(holder, 3));
		}
		crown.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.kings_crown"));
		return crown;
	}

	/** His name: "Grimwald, the Bandit King", the first name {@code index} of the captains' list. */
	public static Component name(int index) {
		List<String> names = Bounties.firstNames();
		String first = names.isEmpty() ? "Grimwald" : names.get(Math.floorMod(index, names.size()));
		return Component.translatable("entity.aliveworkplace.bandit_king", first);
	}

	/**
	 * The King at {@code at}, keeping within {@link BanditFort#KEEP} of {@code yard}; the bandits his horn calls come
	 * out at {@code door}. Null if he can't be made.
	 */
	@Nullable
	public static Mob spawn(ServerLevel level, BlockPos at, BlockPos yard, BlockPos door) {
		Mob king = EntityType.VINDICATOR.create(level);
		if (king == null) {
			return null;
		}
		king.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		king.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		king.setPersistenceRequired();
		king.setCustomName(name(level.random.nextInt(1 << 16)));
		king.setCustomNameVisible(true);
		if (king instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		king.setItemSlot(EquipmentSlot.HEAD, crown(level));
		king.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		king.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		king.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		king.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			king.setDropChance(slot, 0f);
		}
		var health = king.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addPermanentModifier(new AttributeModifier(AliveWorkplace.id("bandit_king"), HEALTH - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
			king.setHealth(king.getMaxHealth());
		}
		king.addTag(TAG);
		king.addTag(DOOR_PREFIX + door.asLong());
		BanditFort.keepTo(king, yard, BanditFort.KEEP);
		if (!io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, king, "Bandit King")) {
			return null; // (said in the log: B208)
		}
		BanditFort.track(king);
		return king;
	}

	/** Looked over every {@link #CHECK_EVERY} ticks: his boss bar, and his horn at half health. */
	static void tick(ServerLevel level, Mob king) {
		bar(level, king);
		if (!king.getTags().contains(HORN_TAG) && king.getHealth() <= king.getMaxHealth() / 2) {
			blowHorn(level, king);
		}
	}

	/** He blows his horn: {@link #CALLED} bandits come out of the keep, once. */
	static void blowHorn(ServerLevel level, Mob king) {
		king.addTag(HORN_TAG);
		level.playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.HOSTILE, 16f, 1f);
		BlockPos door = door(king);
		BlockPos home = BanditFort.home(king);
		for (int i = 0; i < CALLED; i++) {
			BlockPos at = door.offset(i % 2 == 0 ? -1 : 1, 0, i < 2 ? 0 : -1);
			Mob man = BanditFort.man(level, i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER, at, home != null ? home : door, BanditFort.KEEP);
			if (man != null) {
				man.addTag(CALLED_TAG);
				level.sendParticles(ParticleTypes.POOF, man.getX(), man.getY() + 0.5, man.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
				if (king.getLastHurtByMob() != null) {
					man.setTarget(king.getLastHurtByMob());
				}
			}
		}
	}

	private static BlockPos door(Mob king) {
		for (String tag : king.getTags()) {
			if (tag.startsWith(DOOR_PREFIX)) {
				try {
					return BlockPos.of(Long.parseLong(tag.substring(DOOR_PREFIX.length())));
				} catch (NumberFormatException e) {
					break;
				}
			}
		}
		return king.blockPosition();
	}

	private static void bar(ServerLevel level, Mob king) {
		ServerBossEvent bar = BARS.computeIfAbsent(king.getUUID(), k -> new ServerBossEvent(king.getDisplayName(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_10));
		bar.setName(king.getDisplayName());
		bar.setProgress(Math.max(0f, Math.min(1f, king.getHealth() / king.getMaxHealth())));
		// The bar's own list first: a player who went to another dimension, logged out or died is no longer in
		// level.players(), so walking only those would leave him the bar for good (B659).
		for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
			if (!seesBar(level, king, player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (seesBar(level, king, player) && !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	/** Whether {@code player} sees the King's bar: alive, online, in his dimension and within {@link #BAR_RANGE}. */
	private static boolean seesBar(ServerLevel level, Mob king, ServerPlayer player) {
		return !player.isRemoved() && !player.hasDisconnected() && player.isAlive() && player.level() == level
			&& player.distanceToSqr(king) <= (double) BAR_RANGE * BAR_RANGE;
	}

	/** The King's boss bar, or null with none (tests). */
	@Nullable
	public static ServerBossEvent bar(UUID king) {
		return BARS.get(king);
	}

	/** One of the fort's men is gone (dead or unloaded): a King's bar goes with him. */
	static void gone(Mob mob) {
		ServerBossEvent bar = BARS.remove(mob.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** No bar is left showing for a King who isn't loaded in any dimension, wherever its players went (B659). */
	static void dropStaleBars(ServerLevel level) {
		if (BARS.isEmpty()) {
			return;
		}
		BARS.entrySet().removeIf(e -> {
			boolean loaded = false;
			for (ServerLevel l : level.getServer().getAllLevels()) {
				loaded |= l.getEntity(e.getKey()) != null;
			}
			if (!loaded) {
				e.getValue().removeAllPlayers();
				return true;
			}
			return false;
		});
	}

	private BanditKing() {
	}
}
