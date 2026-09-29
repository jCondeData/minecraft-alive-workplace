package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.farm.FieldWork;
import io.github.jcondedata.aliveworkplace.mine.MinerWork;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Porters and what they carry. A porter takes a worker's goods — what their job makes — and leaves what the job
 * needs: the miner's torches, ladders and some stone to patch the stairs, the farmer's seeds, the lumberjack's
 * saplings, the ore and fish waiting for a furnace. Builders and ball smiths make nothing to carry away.
 */
public final class Porters {
	/** Never taken. */
	public static final int ALL = Integer.MAX_VALUE;
	/** Stone of each kind a miner keeps for patching stairs and shafts. */
	public static final int KEEP_FILLER = 32;
	/** Coal a worker with a furnace keeps to burn. */
	public static final int KEEP_FUEL = 32;
	/** Seeds of each kind a farmer keeps. */
	public static final int KEEP_SEEDS = 64;
	/** Saplings (or orchard seeds) of each kind a lumberjack or orchard keeper keeps. */
	public static final int KEEP_SAPLINGS = 32;
	/** Food a shepherd or herder keeps to breed their animals with. */
	public static final int KEEP_BREEDING_FOOD = 16;

	public static boolean isPorter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.PORTER;
	}

	/** Who owns the storehouse at {@code pos}; null for a village's (or if the block is gone). */
	@Nullable
	public static Employer owner(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof StorehouseBlockEntity storehouse ? storehouse.owner() : null;
	}

	/** A porter works for whoever owns their storehouse, and for the village if nobody does. */
	static void answerToOwner(ServerLevel level, Villager porter, BlockPos storehouse) {
		Employer owner = owner(level, storehouse);
		Employer now = porter.getAttached(ModAttachments.BUILDER_EMPLOYER);
		if (Objects.equals(owner == null ? null : owner.id(), now == null ? null : now.id())) {
			return;
		}
		if (owner == null) {
			porter.removeAttached(ModAttachments.BUILDER_EMPLOYER);
		} else {
			porter.setAttached(ModAttachments.BUILDER_EMPLOYER, owner);
		}
		io.github.jcondedata.aliveworkplace.work.Village.forget(porter);
	}

	/** A builder just built a Storehouse: it belongs to whoever the build is for. */
	public static void onBuilt(ServerLevel level, BlockPos pos, BuildSite site) {
		if (!site.ownerName().isEmpty() && level.getBlockEntity(pos) instanceof StorehouseBlockEntity storehouse) {
			storehouse.setOwner(site.owner(), site.ownerName());
		}
	}

	/**
	 * How many of {@code stack}'s kind a worker with this job keeps in their chests: the rest is the porter's to carry.
	 * {@link #ALL} if the porter leaves it alone. Only plain items that stack are ever carried (never tools or gear).
	 */
	public static int keeps(VillagerProfession job, ItemStack stack, boolean furnaceNear) {
		if (stack.getMaxStackSize() <= 1) {
			return ALL;
		}
		if (stack.is(ItemTags.COALS) && furnaceNear) {
			return KEEP_FUEL;
		}
		if (job == ModVillagers.MINER) {
			if (stack.is(Items.TORCH) || stack.is(Items.LADDER) || furnaceNear && Furnaces.isOre(stack.getItem())) {
				return ALL;
			}
			return MinerWork.FILLERS.contains(stack.getItem()) ? KEEP_FILLER : 0;
		}
		if (job == ModVillagers.LUMBERJACK) {
			return stack.is(Items.BONE_MEAL) ? ALL : stack.is(ItemTags.SAPLINGS) ? KEEP_SAPLINGS : 0;
		}
		if (job == ModVillagers.ORCHARD_KEEPER) {
			return stack.is(Items.BONE_MEAL) ? ALL : Orchards.isSeed(stack) ? KEEP_SAPLINGS : 0;
		}
		if (job == VillagerProfession.FARMER) {
			return stack.is(Items.BONE_MEAL) ? ALL : FieldWork.isSeed(stack) ? KEEP_SEEDS : 0;
		}
		if (job == VillagerProfession.ARMORER) {
			// A smelter keeps its ore, and iron for armor; the other ingots go to the storehouse.
			return Furnaces.isOre(stack.getItem()) ? ALL : stack.is(Items.IRON_INGOT) ? io.github.jcondedata.aliveworkplace.smelt.Smelters.KEEP_INGOTS : 0;
		}
		if (job == VillagerProfession.BUTCHER) {
			// Food to breed with, and empty buckets to milk into; the eggs, milk and meat go.
			return stack.is(Items.BUCKET) ? 16 : io.github.jcondedata.aliveworkplace.ranch.HerderWork.isBreedingFood(stack.getItem()) ? KEEP_BREEDING_FOOD : 0;
		}
		if (job == VillagerProfession.SHEPHERD) {
			return stack.is(Items.WHEAT) ? KEEP_BREEDING_FOOD : 0; // wheat to breed with; the wool goes
		}
		if (job == VillagerProfession.CARTOGRAPHER) {
			// An explorer keeps rations and maps; what they bring back goes to the storehouse.
			return io.github.jcondedata.aliveworkplace.explore.Explorers.isFood(stack) ? KEEP_BREEDING_FOOD
				: stack.is(Items.MAP) || stack.is(Items.FILLED_MAP) || stack.is(Items.PAPER) || stack.is(Items.COMPASS) ? ALL : 0;
		}
		if (job == VillagerProfession.FISHERMAN) {
			return furnaceNear && Furnaces.isFish(stack.getItem()) ? ALL : 0;
		}
		return ALL;
	}

	/** Makes {@code villager} a porter at {@code storehouse} right away (tests and admin tools). */
	public static void employ(ServerLevel level, Villager villager, BlockPos storehouse) {
		if (!level.getBlockState(storehouse).is(ModBlocks.STOREHOUSE)) {
			throw new IllegalStateException("no Storehouse at " + storehouse);
		}
		Jobs.employ(level, villager, storehouse, ModVillagers.STOREHOUSE_POI, ModVillagers.PORTER);
	}

	private Porters() {
	}
}
