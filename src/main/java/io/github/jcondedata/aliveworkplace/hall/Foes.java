package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/**
 * What a clearing-out quest ({@link VillageQuests.Kind#SLAY}, a {@code kill} objective of {@code monster}) can count in
 * this world (B301). Cobbleverse ships MobsBeGone, whose blocklist removes the night's monsters, so "Clear out 8
 * monsters" could never be finished there.
 * <ul>
 * <li><b>Monsters can come</b> unless the world is on Peaceful or another mod's spawn blocklist ({@link #BLOCKLIST},
 * filled from MobsBeGone's file by {@code compat/mobsbegone}) refuses every one of the overworld's common night
 * monsters ({@link #NIGHT_MONSTERS}). Both are read from the world and the file, never guessed from what was seen.</li>
 * <li>Without monsters, <b>wild Pokémon</b> a player defeats or catches near the hall count instead when Cobblemon is
 * there ({@link #WILD_POKEMON}), and the quest reads "Defeat 8 wild Pokémon".</li>
 * <li>A <b>foe</b> is any monster, and the mod's own bandits, raiders and lair mobs by their tags (always, as they are
 * forced past a blocklist by {@code forceThreatSpawns}).</li>
 * <li>With neither, no clearing-out quest, reform step or story step goes up; one already up becomes "Bring N Bread"
 * ({@link #SUBSTITUTE}) with the same pay.</li>
 * </ul>
 */
public final class Foes {
	/** Another mod's spawn blocklist: whether it refuses an entity type (by id). Filled by {@code compat/mobsbegone}. */
	public static final Extension<Predicate<ResourceLocation>> BLOCKLIST = new Extension<>("mob spawn blocklist");
	/** Present (true) when wild Pokémon a player defeats or catches can count as foes: filled by {@code compat/cobblemon}. */
	public static final Extension<Boolean> WILD_POKEMON = new Extension<>("wild Pokémon foes");

	/** The overworld's common night monsters: when a blocklist refuses all of them, no monster comes. */
	public static final List<ResourceLocation> NIGHT_MONSTERS = List.of(ResourceLocation.withDefaultNamespace("zombie"),
		ResourceLocation.withDefaultNamespace("skeleton"), ResourceLocation.withDefaultNamespace("creeper"), ResourceLocation.withDefaultNamespace("spider"));

	/** What a clearing-out quest becomes when nothing can be fought: bread for the village, as many as the foes (64 at most). */
	public static final String SUBSTITUTE = "minecraft:bread";

	/** The mod's own foes' tags (bandits, raiders, lair mobs), counted whatever their class. */
	private static final Set<String> TAGS = Set.of(Lairs.TAG, Lairs.BANDIT_TAG, Threats.RAIDER_TAG);

	/** GameTests only: whether monsters can come (null: read the world). */
	@Nullable
	public static volatile Boolean HOSTILES_FOR_TESTS = null;
	/** GameTests only: whether wild Pokémon count (null: whether Cobblemon fills {@link #WILD_POKEMON}). */
	@Nullable
	public static volatile Boolean WILD_FOR_TESTS = null;

	/** The last answer of {@link #hostiles}, for the quest's words (they're written without a level at hand). */
	private static volatile boolean lastHostiles = true;

	/** Whether monsters can come in {@code level}: not Peaceful, and no blocklist refusing every common night monster. */
	public static boolean hostiles(ServerLevel level) {
		Boolean forced = HOSTILES_FOR_TESTS;
		boolean h = forced != null ? forced : level.getDifficulty() != Difficulty.PEACEFUL && !blocklisted();
		lastHostiles = h;
		return h;
	}

	/** Whether another mod's blocklist refuses every one of {@link #NIGHT_MONSTERS}. */
	static boolean blocklisted() {
		return BLOCKLIST.call(p -> NIGHT_MONSTERS.stream().allMatch(p), false);
	}

	/** Whether wild Pokémon defeated or caught can count (Cobblemon's there). */
	public static boolean wild() {
		Boolean forced = WILD_FOR_TESTS;
		return forced != null ? forced : WILD_POKEMON.call(b -> b, false);
	}

	/** Whether a clearing-out quest can be finished in {@code level}: monsters can come, or wild Pokémon count. */
	public static boolean slayable(ServerLevel level) {
		return hostiles(level) || wild();
	}

	/** Whether wild Pokémon count towards clearing-out quests now: only when no monster can come. */
	public static boolean countsWild(ServerLevel level) {
		return !hostiles(level) && wild();
	}

	/** Whether {@code killed} counts as a foe: a monster, or one of the mod's bandits, raiders or lair mobs. */
	public static boolean isFoe(Entity killed) {
		if (killed instanceof Enemy) {
			return true;
		}
		for (String tag : killed.getTags()) {
			if (TAGS.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	/** How many bread a clearing-out quest of {@code count} foes becomes. */
	public static int substituteCount(int count) {
		return Math.max(1, Math.min(64, count));
	}

	/** A clearing-out quest's words: "Clear out 8 monsters", or "Defeat 8 wild Pokémon" where monsters can't come. */
	public static Component line(int count) {
		return Component.translatable(wildWords() ? "quest.aliveworkplace.slay_wild" : "quest.aliveworkplace.slay", count);
	}

	/** Whether the words are the wild Pokémon's: no monster could come when last looked, and Pokémon count. */
	private static boolean wildWords() {
		Boolean forced = HOSTILES_FOR_TESTS;
		return !(forced != null ? forced : lastHostiles) && wild();
	}

	/** The hall page's hint under a clearing-out quest. */
	public static Component hint(int radius) {
		return Component.translatable(wildWords() ? "screen.aliveworkplace.hall.quest_slay_wild_hint"
			: "screen.aliveworkplace.hall.quest_slay_hint", radius);
	}

	private Foes() {
	}
}
