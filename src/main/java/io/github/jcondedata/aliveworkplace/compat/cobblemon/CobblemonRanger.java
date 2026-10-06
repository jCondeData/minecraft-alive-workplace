//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

// The Pokémon Ranger (ROADMAP 29.22) with Cobblemon: the wild Alphas near a village (Cobblemon 1.7's Alpha mark, which
// nothing gives a wild Pokémon yet, or a wild Pokémon of level 50 or more), the wild ones a Ranger may befriend (never a
// legendary, mythical, Ultra Beast or paradox one, as with trainers) and the village's Pasture Blocks they join.
public final class CobblemonRanger implements WildPokemon {
	public static final ResourceLocation ALPHA_MARK = ResourceLocation.fromNamespaceAndPath("cobblemon", "mark_alpha");
	static final Set<String> BANNED = Set.of("legendary", "mythical", "ultra_beast", "paradox");

	// Whether {@code pokemon} bears the Alpha mark (worn, or waiting for its catch).
	public static boolean hasAlphaMark(Pokemon pokemon) {
		for (Mark m : pokemon.getMarks()) {
			if (ALPHA_MARK.equals(m.getIdentifier())) {
				return true;
			}
		}
		for (Mark m : pokemon.getPotentialMarks()) {
			if (ALPHA_MARK.equals(m.getIdentifier())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAlpha(Pokemon pokemon) {
		return hasAlphaMark(pokemon) || pokemon.getLevel() >= PokemonRanger.ALPHA_LEVEL;
	}

	private static AABB box(BlockPos center, int radius) {
		return new AABB(center).inflate(radius, Math.max(32, radius / 2), radius);
	}

	@Override
	public boolean isWild(Entity entity) {
		return entity instanceof PokemonEntity e && e.isAlive() && e.getPokemon().isWild() && e.getOwnerUUID() == null && e.getTethering() == null
			&& !e.isBattling();
	}

	@Override
	public boolean banned(Entity entity) {
		return entity instanceof PokemonEntity e && e.getPokemon().getSpecies().getLabels().stream().anyMatch(BANNED::contains);
	}

	@Override
	public List<Entity> alphas(ServerLevel level, BlockPos center, int radius) {
		double r2 = (double) radius * radius;
		return new ArrayList<>(level.getEntitiesOfClass(PokemonEntity.class, box(center, radius),
			e -> isWild(e) && isAlpha(e.getPokemon()) && e.distanceToSqr(center.getCenter()) <= r2));
	}

	@Override
	public List<Entity> befriendable(ServerLevel level, BlockPos center, int radius) {
		double r2 = (double) radius * radius;
		List<Entity> out = new ArrayList<>(level.getEntitiesOfClass(PokemonEntity.class, box(center, radius),
			e -> isWild(e) && !banned(e) && !e.isBusy() && !e.isUncatchable() && e.distanceToSqr(center.getCenter()) <= r2));
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center.getCenter())));
		return out;
	}

	// The village's Pasture Blocks (their bottom halves hold the block entity), nobody's or {@code owner}'s, nearest first.
	static List<PokemonPastureBlockEntity> pastures(ServerLevel level, BlockPos hall, UUID owner) {
		List<PokemonPastureBlockEntity> out = new ArrayList<>();
		AABB area = VillageHalls.area(hall);
		for (int cx = ((int) Math.floor(area.minX)) >> 4; cx <= ((int) Math.floor(area.maxX)) >> 4; cx++) {
			for (int cz = ((int) Math.floor(area.minZ)) >> 4; cz <= ((int) Math.floor(area.maxZ)) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (be instanceof PokemonPastureBlockEntity pasture && area.contains(be.getBlockPos().getCenter())
						&& (pasture.getOwnerId() == null || pasture.getOwnerId().equals(owner))) {
						out.add(pasture);
					}
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.getBlockPos().distSqr(hall)));
		return out;
	}

	static boolean hasRoom(PokemonPastureBlockEntity pasture) {
		return pasture.getTetheredPokemon().size() < pasture.getMaxTethered();
	}

	@Override
	public Optional<BlockPos> pastureWithRoom(ServerLevel level, BlockPos hall, UUID owner) {
		return pastures(level, hall, owner).stream().filter(CobblemonRanger::hasRoom).map(BlockEntity::getBlockPos).findFirst();
	}

	@Override
	public Befriended befriend(ServerLevel level, Entity wild, BlockPos pasture, UUID owner) {
		if (!isWild(wild) || banned(wild)) {
			return Befriended.REFUSED;
		}
		if (!(level.getBlockEntity(pasture) instanceof PokemonPastureBlockEntity block) || !hasRoom(block)
			|| (block.getOwnerId() != null && !block.getOwnerId().equals(owner))) {
			return Befriended.NO_ROOM;
		}
		PokemonEntity entity = (PokemonEntity) wild;
		Pokemon pokemon = entity.getPokemon();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		PCStore pc = player != null ? Cobblemon.INSTANCE.getStorage().getPC(player) : Cobblemon.INSTANCE.getStorage().getPC(owner, level.registryAccess());
		// Into the owner's PC first, as Cobblemon does with a catch (and as a pasture takes its Pokémon from the PC).
		entity.discard();
		if (!pc.add(pokemon)) {
			return Befriended.REFUSED;
		}
		if (player == null) {
			return Befriended.IN_PC; // the pasture lets out only a Pokémon whose trainer is there
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (block.tether(player, pokemon, side)) {
				return Befriended.PASTURED;
			}
		}
		return Befriended.IN_PC;
	}

	@Override
	public Component name(Entity entity) {
		return entity instanceof PokemonEntity e ? e.getPokemon().getDisplayName(false) : entity.getName();
	}
}
//?}
