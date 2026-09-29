package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mercenaries: from the Village Hall's screen, a player can hire a band of {@link #BAND} fighters for
 * {@link #PRICE_EMERALDS} emeralds (or CobbleDollars) — guards in iron, one with a shield, who fight like the village's
 * own and stay until the next dawn (at least {@link #MIN_STAY} ticks), then leave. One band at a time per village.
 * Their gear never drops, and they don't count as the village's own (no babies, no beds kept).
 */
public final class Mercenaries {
	public static final int BAND = 3;
	public static final int PRICE_EMERALDS = 12;
	/** The shortest time a band stays (hired just before dawn). */
	public static final long MIN_STAY = 6000;

	public static boolean isMercenary(Villager villager) {
		return villager.hasAttached(ModAttachments.MERCENARY_UNTIL);
	}

	/** The band at the hall now (hired and not gone yet). */
	public static List<Villager> near(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && isMercenary(v));
	}

	/** {@code player} hires a band at the hall at {@code hall}; returns what to tell them. */
	public static Component hire(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!near(level, hall).isEmpty()) {
			return Component.translatable("message.aliveworkplace.mercenaries.already").withStyle(ChatFormatting.YELLOW);
		}
		long dollars = (long) PRICE_EMERALDS * Money.DOLLARS_PER_EMERALD;
		if (!Money.charge(player, dollars, PRICE_EMERALDS)) {
			return Component.translatable("message.aliveworkplace.mercenaries.cant_afford", Money.describe(dollars, PRICE_EMERALDS))
				.withStyle(ChatFormatting.RED);
		}
		long until = level.getGameTime() + Math.max(MIN_STAY, (24000 - level.getDayTime() % 24000) % 24000);
		for (int i = 0; i < BAND; i++) {
			BlockPos spot = Walker.standingSpot(level, hall, hall.offset(i - 1, 0, 2), 6.0);
			Villager merc = EntityType.VILLAGER.create(level);
			if (merc == null) {
				continue;
			}
			BlockPos at = spot != null ? spot : hall.above();
			merc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			merc.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			merc.setVillagerData(merc.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
			merc.setVillagerXp(70); // keeps the profession without a guard post
			merc.setAttached(ModAttachments.MERCENARY_UNTIL, until);
			merc.setCustomName(Component.translatable("entity.aliveworkplace.mercenary"));
			merc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			merc.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			merc.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			if (i == 0) {
				merc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			}
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				merc.setDropChance(slot, 0f);
			}
			level.addFreshEntityWithPassengers(merc);
			Guards.updateHealth(merc);
			level.sendParticles(ParticleTypes.CLOUD, merc.getX(), merc.getY() + 0.5, merc.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		}
		level.playSound(null, hall, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 0.6f, 1.4f);
		Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.mercenaries", player.getDisplayName()));
		return Component.translatable("message.aliveworkplace.mercenaries.hired", BAND).withStyle(ChatFormatting.GREEN);
	}

	/** Every server tick of a villager: a mercenary whose time is up leaves. */
	public static void tick(Villager villager) {
		Long until = villager.getAttached(ModAttachments.MERCENARY_UNTIL);
		if (until == null || !(villager.level() instanceof ServerLevel level) || level.getGameTime() < until) {
			return;
		}
		level.sendParticles(ParticleTypes.CLOUD, villager.getX(), villager.getY() + 0.5, villager.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		for (var memory : List.of(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
				net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE, net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT)) {
			villager.releasePoi(memory); // a bed or a post they took is free again
		}
		villager.discard();
	}

	private Mercenaries() {
	}
}
