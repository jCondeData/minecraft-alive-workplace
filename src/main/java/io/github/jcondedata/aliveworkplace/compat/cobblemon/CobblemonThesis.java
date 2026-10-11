//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// The Professor's Thesis (ROADMAP 31.21) with Cobblemon: a capture (POKEMON_CAPTURED, where the ball landed) and a
// Fossil Scientist's revival (CobblemonFossils.revive) count as catches; the party is read for the "show" chapter.
public final class CobblemonThesis implements ProfessorsThesis.Party {
	// Called once at startup (when Cobblemon is installed): listen for captures.
	public static void init() {
		CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
			ServerPlayer player = event.getPlayer();
			BlockPos at = event.getPokeBallEntity() != null ? event.getPokeBallEntity().blockPosition() : player.blockPosition();
			caught(player, event.getPokemon(), at, false);
		});
	}

	// {@code pokemon} was caught by {@code player} at {@code at} (or revived, {@code fossil}): the thesis hears of it.
	public static boolean caught(ServerPlayer player, Pokemon pokemon, BlockPos at, boolean fossil) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		return ProfessorsThesis.onCatch(level, player, info(level, pokemon, at, fossil));
	}

	public static ProfessorsThesis.CatchInfo info(ServerLevel level, Pokemon pokemon, BlockPos at, boolean fossil) {
		List<String> types = new ArrayList<>();
		for (ElementalType t : pokemon.getTypes()) {
			types.add(t.getName().toLowerCase(Locale.ROOT));
		}
		return ProfessorsThesis.CatchInfo.of(level, at, pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier(),
			pokemon.getSpecies().getTranslatedName(), types, fossil);
	}

	@Override
	public List<ProfessorsThesis.Mon> party(ServerPlayer player) {
		List<ProfessorsThesis.Mon> out = new ArrayList<>();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			out.add(new ProfessorsThesis.Mon(p.getUuid(), p.getSpecies().getResourceIdentifier(), p.getFriendship()));
		}
		return out;
	}
}
//?}
