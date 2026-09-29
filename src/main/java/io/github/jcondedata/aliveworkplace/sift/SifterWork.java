package io.github.jcondedata.aliveworkplace.sift;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * A Sifter's shift at the sieve: a block of gravel, sand, dirt or soul sand from the chests by the sieve, shaken through
 * it — a moment a block, quicker with levels and pastured Ground Pokémon — and what comes out (flint, seeds, nuggets, the
 * odd gem; with Cobblemon, now and then an evolution stone) goes into the chests. What each block gives is a loot table,
 * {@code aliveworkplace:sifting/<block>}, so data packs can change it. With nothing to sift, the sifter asks for gravel
 * on the requests board.
 */
public class SifterWork extends Behavior<Villager> {
	/** Ticks a block takes a Novice. */
	static final int SIFT_TICKS = 60;
	/** What can be sifted, and the loot table for each. */
	public static final Map<Item, ResourceKey<LootTable>> SIFTABLE = Map.of(
		Items.GRAVEL, table("gravel"),
		Items.SAND, table("sand"),
		Items.DIRT, table("dirt"),
		Items.SOUL_SAND, table("soul_sand"));

	private static ResourceKey<LootTable> table(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("sifting/" + name));
	}

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private String state = "idle";

	public SifterWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isSifter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.SIFTER;
	}

	public static boolean isSiftable(ItemStack stack) {
		return SIFTABLE.containsKey(stack.getItem());
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isSifter(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos sieve = Builders.benchPos(villager).orElse(null);
		if (sieve == null) {
			return;
		}
		boolean atSieve = walker.walkTo(level, villager, sieve, 2.5);
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.sifter.title", villager.getAttachedOrElse(ModAttachments.BLOCKS_SIFTED, 0)),
			-1f, Component.translatable("message.aliveworkplace.sifter.state." + state).withStyle(state.equals("needs") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		if (!atSieve || --timer > 0) {
			return;
		}
		timer = Math.max(10, Math.round(BuilderLevels.delay(SIFT_TICKS, villager) * Partners.factor(villager)));
		state = sift(level, villager, sieve) == null ? "needs" : "sifting";
	}

	/** Sifts one block from the chests by the sieve; returns what came out, or null if there was nothing to sift. */
	@org.jetbrains.annotations.Nullable
	public static List<ItemStack> sift(ServerLevel level, Villager villager, BlockPos sieve) {
		List<BlockPos> chests = SupplyContainers.find(level, sieve, null);
		ItemStack block = SupplyContainers.takeOne(level, chests, SifterWork::isSiftable);
		if (block.isEmpty()) {
			Requests.post(villager, new ItemStack(Items.GRAVEL), 16, Items.GRAVEL.getDescription(), SifterWork::isSiftable);
			return null;
		}
		Requests.clear(villager);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(SIFTABLE.get(block.getItem()));
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(sieve))
			.withParameter(LootContextParams.THIS_ENTITY, villager)
			.create(LootContextParamSets.GIFT);
		List<ItemStack> found = new java.util.ArrayList<>(table.getRandomItems(params));
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon")) {
			// Now and then an evolution stone (a table that loads only with Cobblemon; missing tables are empty).
			ResourceKey<LootTable> extra = ResourceKey.create(Registries.LOOT_TABLE,
				AliveWorkplace.id("sifting/cobblemon/" + SIFTABLE.get(block.getItem()).location().getPath().substring("sifting/".length())));
			found.addAll(level.getServer().reloadableRegistries().getLootTable(extra).getRandomItems(params));
		}
		for (ItemStack stack : found) {
			ItemStack rest = SupplyContainers.insert(level, chests, stack.copy());
			if (!rest.isEmpty()) {
				villager.spawnAtLocation(rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		Block shown = block.getItem() instanceof net.minecraft.world.item.BlockItem bi ? bi.getBlock() : net.minecraft.world.level.block.Blocks.GRAVEL;
		level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, shown.defaultBlockState()), sieve.getX() + 0.5, sieve.getY() + 1.1,
			sieve.getZ() + 0.5, 12, 0.3, 0.05, 0.3, 0);
		level.playSound(null, sieve, block.is(Items.SAND) ? SoundEvents.SAND_BREAK : SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.6f, 1.1f);
		int sifted = villager.getAttachedOrElse(ModAttachments.BLOCKS_SIFTED, 0) + 1;
		villager.setAttached(ModAttachments.BLOCKS_SIFTED, sifted);
		if (sifted % 4 == 0) {
			BuilderLevels.addXp(level, villager, 1, null);
		}
		return found;
	}
}
