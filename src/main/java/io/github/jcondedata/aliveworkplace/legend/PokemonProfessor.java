package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Pokémon Professor (ROADMAP 29.21), a Legendary Legend without a trade of their own, {@code legends/pokemon_professor.json}
 * (only with Cobblemon): a guest at the inn once the village's Pasture Blocks hold 25 Pokémon of 10 types
 * ({@code pastured_pokemon}, counted by {@link PokemonCensus}). Likes books. Their powers:
 * <ul>
 *   <li>{@code pokemon_hints}: right-clicked with an empty hand, they show the player's party, and for each Pokémon each
 *   stat's IV in words ({@link #ivWord}), the stats its nature raises and lowers, whether it has its hidden ability, and
 *   its EVs (in words, exact numbers with Regional Survey);</li>
 *   <li>{@code pokedex}: while they live in the village, every species kept in its pastures is logged in the hall
 *   ({@link VillageHallBlockEntity#pokedex}), counted on the hall's Legends page and as the research counter
 *   {@link #COUNTER}, which opens the topics of their research tree, the Pokédex ({@code research_trees/pokedex.json}).</li>
 * </ul>
 * The tree's topics are flags read here: Field Notes (partners help 5% more a level), Kinship Studies (one more partner
 * per worker), Breeding Records (daycare eggs 20% sooner a level), Berry Science (orchard keepers pick one more berry from
 * each berry plant), Evolution Studies (the Professor sells one evolution stone a day for {@link #STONE_PRICE} emeralds)
 * and Regional Survey (the hints show exact IVs and EVs).
 */
public final class PokemonProfessor {
	public static final ResourceLocation ID = AliveWorkplace.id("pokemon_professor");
	public static final ResourceLocation TREE = AliveWorkplace.id("pokedex");
	/** The research counter the Pokédex tree's topics wait for: species in the village Pokédex. */
	public static final String COUNTER = "pokedex_species";

	public static final String FIELD_NOTES = "field_notes";
	public static final String KINSHIP_STUDIES = "kinship_studies";
	public static final String BREEDING_RECORDS = "breeding_records";
	public static final String BERRY_SCIENCE = "berry_science";
	public static final String EVOLUTION_STUDIES = "evolution_studies";
	public static final String REGIONAL_SURVEY = "regional_survey";

	/** Field Notes: how much more each partner helps, a level (a share of its help). */
	public static final float FIELD_NOTES_BONUS = 0.05f;
	/** Breeding Records: how much sooner daycare eggs come, a level (a share of the wait). */
	public static final float BREEDING_SOONER = 0.2f;
	/** Evolution Studies: an evolution stone's price, in emeralds. */
	public static final int STONE_PRICE = 8;
	/** Cobblemon's evolution stones, in the order the days offer them (any the installed Cobblemon lacks are skipped). */
	public static final List<String> STONES = List.of("fire_stone", "water_stone", "thunder_stone", "leaf_stone", "moon_stone", "sun_stone",
		"shiny_stone", "dusk_stone", "dawn_stone", "ice_stone");
	/** The hall's Legends page: the village Pokédex's button (the anthem's is at 8). */
	public static final int POKEDEX_SLOT = 7;
	/** Species the Pokédex button names, the latest first. */
	static final int SHOWN = 6;

	/** {@code pokemon_hints}: the party's IVs, nature, hidden ability and EVs. */
	public record HintsPower() implements Power {
		static HintsPower read(JsonObject json) {
			return new HintsPower();
		}

		@Override
		public String type() {
			return "pokemon_hints";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.pokemon_hints");
		}
	}

	/** {@code pokedex}: every species kept in the village's pastures logged in the hall. */
	public record PokedexPower() implements Power {
		static PokedexPower read(JsonObject json) {
			return new PokedexPower();
		}

		@Override
		public String type() {
			return "pokedex";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.pokedex");
		}
	}

	static void register() {
		Powers.register("pokemon_hints", HintsPower::read);
		Powers.register("pokedex", PokedexPower::read);
	}

	/** Adds the research counter (at start-up). */
	public static void init() {
		ResearchTrees.counter(COUNTER, Component.translatable("research_tree.aliveworkplace.counter.pokedex_species"),
			(level, hall) -> level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.pokedex().size() : 0);
	}

	// ---- who ----

	private static Optional<Legend> legend(Villager villager) {
		if (!Legends.ENABLED) {
			return Optional.empty();
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled()) {
			return Optional.empty();
		}
		return Legends.get(data.id());
	}

	/** Whether {@code villager} is a settled Legend with the hints (on strike or not: they still answer). */
	public static boolean isProfessor(Villager villager) {
		return legend(villager).map(l -> !l.powers(HintsPower.class).isEmpty()).orElse(false);
	}

	/** Whether {@code villager} is a settled Professor at work (not on strike). */
	public static boolean working(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return isProfessor(villager) && data != null && !data.onStrike();
	}

	/** Right-clicked with an empty hand: the hints screen (a Professor on strike gives none). */
	public static void open(ServerPlayer player, Villager professor) {
		if (!working(professor)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.professor.on_strike", professor.getDisplayName())
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!PokemonCensus.EXTENSION.present()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.professor.no_pokemon", professor.getDisplayName())
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		PokemonCensus.EXTENSION.run(c -> c.openHints(player, professor));
	}

	// ---- the hints, in words ----

	/** An IV in the words of the games' judge: No good (0), Decent (1-15), Pretty good (16-25), Very good (26-29), Fantastic (30), Best (31). */
	public static String ivKey(int iv) {
		if (iv <= 0) {
			return "no_good";
		}
		if (iv <= 15) {
			return "decent";
		}
		if (iv <= 25) {
			return "pretty_good";
		}
		if (iv <= 29) {
			return "very_good";
		}
		return iv == 30 ? "fantastic" : "best";
	}

	public static MutableComponent ivWord(int iv) {
		return Component.translatable("message.aliveworkplace.professor.iv." + ivKey(iv));
	}

	/** EVs in words: none (0), a little (1-63), some (64-127), a lot (128-251), full (252, the most one stat takes). */
	public static String evKey(int ev) {
		if (ev <= 0) {
			return "none";
		}
		if (ev < 64) {
			return "a_little";
		}
		if (ev < 128) {
			return "some";
		}
		return ev < 252 ? "a_lot" : "full";
	}

	public static MutableComponent evWord(int ev) {
		return Component.translatable("message.aliveworkplace.professor.ev." + evKey(ev));
	}

	/** One stat's line: "HP: Best", or with Regional Survey "HP: Best (31 IV, 4 EV)". */
	public static MutableComponent statLine(Component stat, int iv, int ev, boolean exact) {
		return exact ? Component.translatable("message.aliveworkplace.professor.stat_exact", stat, ivWord(iv), iv, ev)
			: Component.translatable("message.aliveworkplace.professor.stat", stat, ivWord(iv), evWord(ev));
	}

	/** Whether the hints in {@code professor}'s village show exact IVs and EVs (Regional Survey). */
	public static boolean exact(Villager professor) {
		return TreeEffects.flag(professor, REGIONAL_SURVEY);
	}

	// ---- the tree's effects ----

	/** Field Notes: the share more each partner of {@code worker} helps (0.05 a level). */
	public static float partnerBonus(Villager worker) {
		return FIELD_NOTES_BONUS * TreeEffects.flagCount(worker, FIELD_NOTES);
	}

	/** Kinship Studies: more partners per worker. */
	public static int extraPartners(Villager worker) {
		return TreeEffects.flagCount(worker, KINSHIP_STUDIES);
	}

	/** Breeding Records: a daycare keeper's dawn odds of an egg, with eggs coming 20% sooner a level (the wait shortened, the odds raised to match; at most 100). */
	public static int eggOdds(Villager keeper, int odds) {
		int levels = Math.min(4, TreeEffects.flagCount(keeper, BREEDING_RECORDS));
		if (odds <= 0 || levels <= 0) {
			return odds;
		}
		return Math.min(100, Math.round(odds / (1f - BREEDING_SOONER * levels)));
	}

	/** Berry Science: berries more an orchard keeper picks from each berry plant. */
	public static int extraBerries(Villager picker) {
		return TreeEffects.flag(picker, BERRY_SCIENCE) ? 1 : 0;
	}

	// ---- the village Pokédex ----

	/** Whether a settled Professor with the Pokédex works in the village round {@code hall}. */
	public static Optional<Villager> keeper(ServerLevel level, BlockPos hall) {
		for (LegendPowers.Active a : LegendPowers.settled(level)) {
			if (!a.legend().powers(PokedexPower.class).isEmpty() && a.villager().isAlive()
				&& a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false))) {
				return Optional.of(a.villager());
			}
		}
		return Optional.empty();
	}

	/**
	 * The hall's round: with a Professor at work in the village, every species in its pastures not yet in the Pokédex is
	 * logged; the village's players are told, and a count that opens a Pokédex topic goes in the chronicle.
	 */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!PokemonCensus.EXTENSION.present() || keeper(level, hall).isEmpty()) {
			return;
		}
		log(level, hall, entity, PokemonCensus.of(level, hall).species());
	}

	/** Logs {@code species} in the village's Pokédex (each once); returns the new ones. */
	public static List<String> log(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, java.util.Collection<String> species) {
		int before = entity.pokedex().size();
		List<String> added = entity.logPokedex(species);
		if (added.isEmpty()) {
			return added;
		}
		int after = entity.pokedex().size();
		MutableComponent names = Component.empty();
		for (int i = 0; i < added.size(); i++) {
			if (i > 0) {
				names.append(", ");
			}
			names.append(PokemonCensus.name(added.get(i)));
		}
		Component text = Component.translatable("message.aliveworkplace.professor.logged", VillageHalls.name(level, hall), names, after)
			.withStyle(ChatFormatting.AQUA);
		double r = VillageHalls.RADIUS;
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < r * r)) {
			Chat.chat(player, text);
		}
		ResearchTrees.get(TREE).ifPresent(tree -> {
			for (ResearchTree.Topic topic : tree.topics()) {
				if (topic.unlock().isPresent() && topic.unlock().get().counter().equals(COUNTER)
					&& topic.unlock().get().at() > before && topic.unlock().get().at() <= after) {
					Chronicle.record(level, hall, Chronicle.Kind.RESEARCH, Component.translatable("chronicle.aliveworkplace.professor.opened",
						topic.unlock().get().at(), topic.name()));
				}
			}
		});
		return added;
	}

	/** The Pokédex topic the village's count waits for next, and at what count (empty: every topic open). */
	public static Optional<ResearchTree.Topic> nextTopic(int count) {
		return ResearchTrees.get(TREE).flatMap(tree -> tree.topics().stream()
			.filter(t -> t.unlock().isPresent() && t.unlock().get().counter().equals(COUNTER) && t.unlock().get().at() > count)
			.min(java.util.Comparator.comparingInt(t -> t.unlock().get().at())));
	}

	/** The hall's Legends page: the village Pokédex, its count, the latest species and the next topic it opens (with Cobblemon). */
	public static void button(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		if (!PokemonCensus.EXTENSION.present() || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		menu.button(POKEDEX_SLOT, icon(level, hall, entity), null);
	}

	/** The village Pokédex as an icon: its count, the latest species, the next topic it opens. */
	public static ItemStack icon(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		int count = entity.pokedex().size();
		List<Component> lines = new ArrayList<>();
		lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pokedex.count", count), count > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		List<String> species = new ArrayList<>(entity.pokedex());
		for (int i = species.size() - 1; i >= Math.max(0, species.size() - SHOWN); i--) {
			lines.add(VillageHallScreen.line(Component.literal("• ").append(PokemonCensus.name(species.get(i))), ChatFormatting.WHITE));
		}
		nextTopic(count).ifPresent(t -> lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pokedex.next",
			t.unlock().get().at(), t.name()), ChatFormatting.YELLOW)));
		lines.add(VillageHallScreen.line(Component.translatable(keeper(level, hall).isPresent() ? "screen.aliveworkplace.pokedex.about"
			: "screen.aliveworkplace.pokedex.no_professor"), ChatFormatting.GRAY));
		return VillageHallScreen.icon(Items.BOOK, Component.translatable("screen.aliveworkplace.pokedex.title", VillageHalls.name(level, hall)),
			ChatFormatting.RED, lines.toArray(Component[]::new));
	}

	// ---- Evolution Studies: a stone a day ----

	/** The evolution stones the installed Cobblemon has. */
	public static List<Item> stones() {
		List<Item> out = new ArrayList<>();
		for (String s : STONES) {
			BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", s)).ifPresent(out::add);
		}
		return out;
	}

	/** Today's stone (the days go round the stones), or null without any. */
	@org.jetbrains.annotations.Nullable
	public static Item stoneOfDay(ServerLevel level) {
		List<Item> stones = stones();
		return stones.isEmpty() ? null : stones.get((int) Math.floorMod(Chronicle.day(level), (long) stones.size()));
	}

	/** Whether the Professor's village has Evolution Studies. */
	public static boolean sellsStones(Villager professor) {
		return TreeEffects.flag(professor, EVOLUTION_STUDIES);
	}

	/** Whether today's stone is already sold. */
	public static boolean soldToday(Villager professor) {
		Long day = ModAttachments.PROFESSOR_STONE.get(professor);
		return day != null && day == Chronicle.day((ServerLevel) professor.level());
	}

	/** {@code player} buys today's stone for {@link #STONE_PRICE} emeralds (or their worth in CobbleDollars); what to tell them. */
	public static Component buyStone(ServerPlayer player, Villager professor) {
		ServerLevel level = (ServerLevel) professor.level();
		Item stone = stoneOfDay(level);
		if (stone == null || !working(professor) || !sellsStones(professor)) {
			return Component.translatable("message.aliveworkplace.professor.no_stone", professor.getDisplayName()).withStyle(ChatFormatting.GRAY);
		}
		if (soldToday(professor)) {
			return Component.translatable("message.aliveworkplace.professor.stone_sold", professor.getDisplayName()).withStyle(ChatFormatting.GRAY);
		}
		if (!Money.charge(player, (long) STONE_PRICE * Money.DOLLARS_PER_EMERALD, STONE_PRICE)) {
			return Component.translatable("message.aliveworkplace.professor.stone_cant_afford", Money.describe((long) STONE_PRICE * Money.DOLLARS_PER_EMERALD, STONE_PRICE))
				.withStyle(ChatFormatting.RED);
		}
		ModAttachments.PROFESSOR_STONE.set(professor, Chronicle.day(level));
		ItemStack bought = new ItemStack(stone);
		Component name = bought.getHoverName();
		if (!player.getInventory().add(bought)) {
			player.drop(bought, false);
		}
		level.playSound(null, professor.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return Component.translatable("message.aliveworkplace.professor.stone_bought", name, professor.getDisplayName()).withStyle(ChatFormatting.GREEN);
	}

	private PokemonProfessor() {
	}
}
