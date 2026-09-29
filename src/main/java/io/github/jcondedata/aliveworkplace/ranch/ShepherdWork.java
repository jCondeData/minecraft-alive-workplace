package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Shepherd's shift (vanilla Shepherds, at their loom): shear the sheep around the loom — and, with Cobblemon, the
 * woolly Pokémon kept in a pasture there (Wooloo, Dubwool) — with shears from the chests, pick up the wool, breed the
 * sheep with wheat up to {@link RanchWork#CAP}, and put it all in the chests by the loom.
 */
public class ShepherdWork extends RanchWork {
	private static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");

	public static boolean isShepherd(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.SHEPHERD;
	}

	@Override
	protected boolean isOurs(Villager villager) {
		return isShepherd(villager);
	}

	@Override
	protected Set<EntityType<?>> herd() {
		return Set.of(EntityType.SHEEP);
	}

	@Override
	protected boolean isDrop(ItemStack stack) {
		return stack.is(ItemTags.WOOL) || stack.is(Items.MUTTON);
	}

	/** The nearest sheep (or pastured woolly Pokémon) ready for shearing, if there are shears to hand. */
	@Nullable
	@Override
	protected Entity tendTarget(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		if (!villager.getMainHandItem().is(Items.SHEARS) && SupplyContainers.firstWith(level, own, Items.SHEARS) == null) {
			return null;
		}
		return level.getEntitiesOfClass(Entity.class, new AABB(station).inflate(RADIUS, 6, RADIUS), ShepherdWork::shearable).stream()
			.min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
	}

	static boolean shearable(Entity entity) {
		if (!entity.isAlive() || !(entity instanceof Shearable shearable) || !shearable.readyForShearing()) {
			return false;
		}
		return entity instanceof Sheep || COBBLEMON && io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners.isPastured(entity);
	}

	/** Shears in hand first, from the chests. */
	@Nullable
	@Override
	protected Boolean prepare(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		ItemStack held = villager.getMainHandItem();
		if (held.is(Items.SHEARS)) {
			return true;
		}
		BlockPos chest = SupplyContainers.firstWith(level, own, Items.SHEARS);
		if (chest == null) {
			return null;
		}
		if (!walker.walkTo(level, villager, chest, 3.0)) {
			return false;
		}
		ItemStack got = SupplyContainers.takeOne(level, own, s -> s.is(Items.SHEARS));
		if (got.isEmpty()) {
			return null;
		}
		if (!held.isEmpty()) {
			bag.add(held);
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, got);
		villager.setDropChance(EquipmentSlot.MAINHAND, 1f);
		walker.reset();
		return true;
	}

	@Override
	protected boolean tend(ServerLevel level, Villager villager, Entity animal, List<BlockPos> own, BuilderBag bag) {
		ItemStack shears = villager.getMainHandItem();
		if (!shears.is(Items.SHEARS) || !shearable(animal)) {
			return false;
		}
		((Shearable) animal).shear(SoundSource.NEUTRAL);
		villager.swing(InteractionHand.MAIN_HAND);
		shears.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		villager.setAttached(ModAttachments.ANIMALS_SHEARED, villager.getAttachedOrElse(ModAttachments.ANIMALS_SHEARED, 0) + 1);
		BuilderLevels.addXp(level, villager, 1, null);
		return false; // done with this one
	}

	@Override
	protected void status(Villager villager, Task task, boolean noChest) {
		Component title = Component.translatable("message.aliveworkplace.shepherd.title", villager.getAttachedOrElse(ModAttachments.ANIMALS_SHEARED, 0));
		String state = noChest ? "no_chest" : task.name().toLowerCase();
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.shepherd.state." + state)
			.withStyle(noChest ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
