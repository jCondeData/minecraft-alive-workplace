package io.github.jcondedata.aliveworkplace.grave;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Graves: a grown villager with a job (or a name) who dies leaves a grave where they fell, holding everything they were —
 * job, level, trades, name. An Undertaker brings them back with a golden apple, a healing potion or a totem of undying
 * ({@link UndertakerWork}). A villager a zombie turns into a zombie villager leaves no grave (cure them instead).
 */
public final class Graves {
	/** Whether villagers leave graves at all. */
	public static boolean ENABLED = true;

	/** Leaves a grave for a villager who just died, if they're one who gets one and there's room. */
	@Nullable
	public static BlockPos onDeath(ServerLevel level, Villager villager) {
		if (!ENABLED || villager.isRemoved() || villager.isBaby()) {
			return null; // turned into a zombie villager, or a child
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		boolean worker = job != VillagerProfession.NONE && job != VillagerProfession.NITWIT;
		if (!worker && !villager.hasCustomName()) {
			return null;
		}
		BlockPos spot = spot(level, villager.blockPosition());
		if (spot == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		Direction facing = Direction.from2DDataValue(level.random.nextInt(4));
		level.setBlockAndUpdate(spot, ModBlocks.GRAVE.defaultBlockState().setValue(GraveBlock.FACING, facing));
		if (level.getBlockEntity(spot) instanceof GraveBlockEntity grave) {
			grave.fill(tag, villager.getDisplayName(), BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString(),
				villager.getVillagerData().getLevel(), level.getGameTime());
		}
		level.playSound(null, spot, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.5f, 0.6f);
		return spot;
	}

	/** Where the grave goes: the nearest open spot on solid ground within 3 blocks. */
	@Nullable
	static BlockPos spot(ServerLevel level, BlockPos at) {
		for (int r = 0; r <= 3; r++) {
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
				BlockState state = level.getBlockState(p);
				if ((state.isAir() || state.canBeReplaced()) && level.getFluidState(p).isEmpty()
					&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	/** "Here lies Mira, Journeyman Builder. An Undertaker can bring them back…" */
	public static Component epitaph(GraveBlockEntity grave) {
		ResourceLocation id = ResourceLocation.tryParse(grave.profession());
		Component job = id == null || id.getPath().equals("none") ? Component.translatable("entity.minecraft.villager")
			: Component.translatable("entity.minecraft.villager." + id.getPath());
		return Component.translatable("message.aliveworkplace.grave.epitaph", grave.name(), BuilderLevels.levelName(grave.villagerLevel()), job)
			.withStyle(ChatFormatting.GRAY);
	}

	/** What an undertaker brings a villager back with. */
	public static boolean isRevivalItem(ItemStack stack) {
		if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.TOTEM_OF_UNDYING)) {
			return true;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		return potion != null && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION))
			&& java.util.stream.StreamSupport.stream(potion.getAllEffects().spliterator(), false).anyMatch(e -> e.is(MobEffects.HEAL));
	}

	/** The graves within {@code radius} of {@code pos}, nearest first. */
	public static List<BlockPos> near(ServerLevel level, BlockPos pos, int radius) {
		return level.getPoiManager().findAll(h -> h.is(ModVillagers.GRAVE_POI), p -> true, pos, radius, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(pos))).toList();
	}

	/** Brings back whoever lies in the grave at {@code pos}: as they were, on their feet beside it; the grave goes. */
	@Nullable
	public static Villager revive(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof GraveBlockEntity grave) || grave.villager().isEmpty()) {
			return null;
		}
		CompoundTag tag = grave.villager().copy();
		tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VILLAGER).toString());
		for (String gone : new String[] {"Pos", "Motion", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "active_effects", "FallDistance"}) {
			tag.remove(gone);
		}
		Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
			e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, e.getYRot(), 0f);
			return e;
		});
		if (!(entity instanceof Villager villager)) {
			return null;
		}
		UUID was = villager.getUUID();
		if (level.getEntity(villager.getUUID()) != null) {
			villager.setUUID(UUID.randomUUID());
		}
		villager.setHealth(villager.getMaxHealth());
		villager.deathTime = 0;
		// The work they were doing was handed back when they died.
		ModAttachments.BUILDER_JOB.remove(villager);
		ModAttachments.MINER_JOB.remove(villager);
		ModAttachments.TREE_FARM.remove(villager);
		ModAttachments.ORCHARD.remove(villager);
		ModAttachments.BERRY_PLOT.remove(villager);
		ModAttachments.FARM_FIELD.remove(villager);
		level.removeBlock(pos, false);
		if (!level.addFreshEntity(villager)) {
			return null;
		}
		// Their workstation back, if nobody took it meanwhile; they find a bed again themselves.
		villager.getBrain().eraseMemory(MemoryModuleType.HOME);
		GlobalPos job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (job != null && job.dimension().equals(level.dimension())) {
			boolean taken = level.getPoiManager().take(h -> true, (h, p) -> p.equals(job.pos()), job.pos(), 1).isPresent();
			if (!taken) {
				villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			}
		}
		villager.refreshBrain(level);
		ModAttachments.REVIVED.set(villager, true); // back from the grave: something to tell a friend (31.7)
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.0, villager.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.6f, 1.1f);
		io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, pos, io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.REVIVED, Component.translatable("chronicle.aliveworkplace.revived", villager.getDisplayName()));
		io.github.jcondedata.aliveworkplace.legend.LegendSlots.onRevived(level, was, villager); // a Legend back as they were
		return villager;
	}

	private Graves() {
	}
}
