package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRanger;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.AlphaNear;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-d-1027-1612): ROADMAP 29.21 (the Pokémon Professor) and 29.22 (the Pokémon Ranger) checked from
 * their specs with the real Cobblemon, aiming at what {@link ProfessorCompatTests} and {@link RangerCompatTests} leave
 * open: the census exactly at 25 Pokémon and 10 types (and one short of each), a species kept after it leaves the
 * pasture, the judge's IV words at every boundary, natures raising and lowering the right stats, the hints opened by a
 * real right-click, the Alpha stand-in at level 49 and 50, the 96-block reach from both sides, every banned kind of
 * Pokémon (mythical, Ultra Beast, paradox, legendary), the 64-block befriending reach, and no befriending into a full
 * pasture.
 */
public class QaPokemonLegendsCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A hall in the middle (radius 14), Legend needs off, Pokémon and villagers far round cleared; everything put back after. */
	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static Legend legend(net.minecraft.resources.ResourceLocation id) {
		return Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load with Cobblemon"));
	}

	private static Villager make(GameTestHelper helper, net.minecraft.resources.ResourceLocation id, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend(id), "test");
		LegendPowers.forget();
		return v;
	}

	private static PokemonEntity wild(GameTestHelper helper, String properties, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse(properties, " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	/**
	 * Held where it was put: the reach spots lie outside this test's area, over other tests' areas, where a Pokémon
	 * could fall into a hole (and so stand farther off than the distance under test) or choke in a leftover block.
	 */
	private static PokemonEntity pinned(PokemonEntity e) {
		e.setNoGravity(true);
		e.setInvulnerable(true);
		e.setDeltaMovement(Vec3.ZERO);
		return e;
	}

	private static ServerPlayer owner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		hall(helper).setOwner(player.getUUID(), player.getGameProfile().getName());
		return player;
	}

	/** This class's own chunk ticket: a chunk another test forces and lets go of stays loaded for this one (B647). */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
		net.minecraft.server.level.TicketType.create("aliveworkplace_qa_legends_test", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	/**
	 * Keeps the chunks at {@code relative} spots loaded and ticking for this test (far points of the reach checks). B647:
	 * with the "forced" flag, a chunk another test had already forced wasn't this test's, and that test's unforcing it
	 * mid-way left the far spot unloaded ("the chunk ... never took entities"); a ticket of its own can't be taken away.
	 */
	private static void forceChunks(GameTestHelper helper, BlockPos... relative) {
		ServerLevel level = helper.getLevel();
		List<net.minecraft.world.level.ChunkPos> held = new ArrayList<>();
		for (BlockPos p : relative) {
			net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(helper.absolutePos(p));
			if (!held.contains(pos)) {
				level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
				held.add(pos);
			}
			level.getChunk(pos.x, pos.z);
		}
		PartnerShowsCompatTests.after(helper, () -> held.forEach(pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos)));
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static Object arg(Component c, int i) {
		return ((TranslatableContents) c.getContents()).getArgs()[i];
	}

	private static Pokemon pokemon(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** 24 single-type species of 9 types: normal 4, bug 1, fire 4, water 4, grass 1, electric 3, fighting 2, psychic 2, ground 3. */
	private static final List<String> NINE_TYPES = List.of("rattata", "meowth", "eevee", "snorlax", "caterpie", "charmander", "vulpix", "growlithe",
		"ponyta", "squirtle", "psyduck", "poliwag", "magikarp", "tangela", "pikachu", "voltorb", "electabuzz", "machop", "mankey", "abra",
		"drowzee", "sandshrew", "cubone", "diglett");

	// ---------------------------------------------------------------- the Professor (29.21)

	/**
	 * The Professor's condition, from both sides: 24 Pokémon of 9 types isn't enough; a 25th of a type already there (25
	 * of 9 types) still isn't, and its bar stays one short; with that one let go and a Dratini (the 10th type) in its
	 * place, 25 of 10 types, it's met. The hall's line reads the counts.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_census"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_census")
	public void censusOpensAtExactly25Of10Types(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos[] pastures = {
			PastureCompatTests.pasture(helper, new BlockPos(5, 2, 5)),
			PastureCompatTests.pasture(helper, new BlockPos(25, 2, 5)),
			PastureCompatTests.pasture(helper, new BlockPos(5, 2, 25)),
			PastureCompatTests.pasture(helper, new BlockPos(25, 2, 25))};
		for (int i = 0; i < NINE_TYPES.size(); i++) {
			PastureCompatTests.pastured(helper, pastures[i % 4], player, NINE_TYPES.get(i), SIDES[(i / 4) % 4]);
		}
		helper.runAfterDelay(10, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			Condition condition = legend(PokemonProfessor.ID).conditions().get(0);
			Condition.Progress p24 = condition.progress(level, hall);
			helper.assertTrue(!p24.met() && p24.have() == 24, "24 Pokémon of 9 types: met " + p24.met() + ", have " + p24.have());
			helper.assertTrue(p24.line().getString().equals("Pokémon in the village's Pasture Blocks: 24 of 25, of 9 of 10 types"), "line: " + p24.line().getString());
			Pokemon ditto = PastureCompatTests.pastured(helper, pastures[0], player, "ditto", Direction.EAST);
			helper.runAfterDelay(5, () -> {
				Condition.Progress p25 = condition.progress(level, hall);
				helper.assertTrue(census(helper).pokemon() == 25, "census: " + census(helper).pokemon());
				helper.assertTrue(!p25.met() && p25.have() == 24, "25 Pokémon of only 9 types: met " + p25.met() + ", have " + p25.have());
				helper.assertTrue(p25.line().getString().equals("Pokémon in the village's Pasture Blocks: 25 of 25, of 9 of 10 types"), "line: " + p25.line().getString());
				((PokemonPastureBlockEntity) level.getBlockEntity(pastures[0])).releasePokemon(ditto.getUuid());
				PastureCompatTests.pastured(helper, pastures[1], player, "dratini", Direction.EAST);
				helper.runAfterDelay(5, () -> {
					var count = census(helper);
					helper.assertTrue(count.pokemon() == 25 && count.types().size() == 10, "after the swap: " + count.pokemon() + " of " + count.types());
					Condition.Progress met = condition.progress(level, hall);
					helper.assertTrue(met.met() && met.have() == 25, "25 Pokémon of 10 types: met " + met.met() + ", have " + met.have());
					helper.succeed();
				});
			});
		});
	}

	private static io.github.jcondedata.aliveworkplace.legend.PokemonCensus.Count census(GameTestHelper helper) {
		return io.github.jcondedata.aliveworkplace.legend.PokemonCensus.of(helper.getLevel(), helper.absolutePos(HALL));
	}

	/**
	 * The village Pokédex is every species ever kept: a Pikachu let out of the pasture stays logged (the census drops it),
	 * a second Pikachu later doesn't log it twice, and a batch naming one species twice logs it once.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_pokedex"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_pokedex")
	public void aReleasedSpeciesStaysLoggedOnce(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		Pokemon first = PastureCompatTests.pastured(helper, pasture, player, "pikachu", Direction.NORTH);
		helper.runAfterDelay(10, () -> {
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = hall(helper);
			make(helper, PokemonProfessor.ID, new BlockPos(13, 2, 13));
			PokemonProfessor.round(level, hallPos, hall);
			helper.assertTrue(hall.pokedex().equals(Set.of("cobblemon:pikachu")), "logged: " + hall.pokedex());
			((PokemonPastureBlockEntity) level.getBlockEntity(pasture)).releasePokemon(first.getUuid());
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(census(helper).pokemon() == 0, "the released Pikachu is still counted: " + census(helper));
				PokemonProfessor.round(level, hallPos, hall);
				helper.assertTrue(hall.pokedex().equals(Set.of("cobblemon:pikachu")), "a species once kept was forgotten: " + hall.pokedex());
				PastureCompatTests.pastured(helper, pasture, player, "pikachu", Direction.SOUTH);
				helper.runAfterDelay(5, () -> {
					PokemonProfessor.round(level, hallPos, hall);
					helper.assertTrue(hall.pokedex().size() == 1, "a second Pikachu logged again: " + hall.pokedex());
					List<String> added = PokemonProfessor.log(level, hallPos, hall, List.of("cobblemon:eevee", "cobblemon:eevee", "cobblemon:pikachu"));
					helper.assertTrue(added.equals(List.of("cobblemon:eevee")), "logged in one batch: " + added);
					helper.assertTrue(hall.pokedex().size() == 2, "the Pokédex: " + hall.pokedex());
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The IV words are the games' judge's, at every boundary from both sides: 0 No good, 1-15 Decent, 16-25 Pretty
	 * good, 26-29 Very good, 30 Fantastic, 31 Best; and the hints read them for a real Pokémon with those IVs.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_legends_ivs"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_legends_ivs")
	public void ivWordsChangeAtTheJudgesBoundaries(GameTestHelper helper) {
		String[] expected = new String[32];
		for (int iv = 0; iv <= 31; iv++) {
			expected[iv] = iv == 0 ? "No good" : iv <= 15 ? "Decent" : iv <= 25 ? "Pretty good" : iv <= 29 ? "Very good" : iv == 30 ? "Fantastic" : "Best";
		}
		for (int iv = 0; iv <= 31; iv++) {
			String said = PokemonProfessor.ivWord(iv).getString();
			helper.assertTrue(said.equals(expected[iv]), "IV " + iv + ": \"" + said + "\", the judge says \"" + expected[iv] + "\"");
		}
		int[][] sets = {{1, 15, 16, 25, 26, 29}, {0, 30, 31, 15, 16, 30}};
		for (int[] ivs : sets) {
			Pokemon p = pokemon("eevee level=20 nature=hardy");
			for (int i = 0; i < 6; i++) {
				p.getIvs().set(CobblemonProfessor.STATS.get(i), ivs[i]);
				p.getEvs().set(CobblemonProfessor.STATS.get(i), 0);
			}
			List<Component> hints = CobblemonProfessor.hints(p, false);
			for (int i = 0; i < 6; i++) {
				String line = hints.get(1 + i).getString();
				String want = CobblemonProfessor.STATS.get(i).getDisplayName().getString() + ": " + expected[ivs[i]] + ", no EVs";
				helper.assertTrue(line.equals(want), "IV " + ivs[i] + ": \"" + line + "\", wanted \"" + want + "\"");
			}
			List<Component> exact = CobblemonProfessor.hints(p, true);
			for (int i = 0; i < 6; i++) {
				String line = exact.get(1 + i).getString();
				String want = CobblemonProfessor.STATS.get(i).getDisplayName().getString() + ": " + expected[ivs[i]] + " (IV " + ivs[i] + "), EVs 0";
				helper.assertTrue(line.equals(want), "exact: \"" + line + "\", wanted \"" + want + "\"");
			}
		}
		helper.succeed();
	}

	/**
	 * Natures: the stat raised and the one lowered, for natures touching every stat but HP (Timid, Modest, Bold,
	 * Careful, Brave), the sentence as the player reads it; every neutral nature raises and lowers nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_legends_natures"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_legends_natures")
	public void natureHintNamesTheRaisedAndLoweredStats(GameTestHelper helper) {
		Object[][] natures = {
			{"timid", Stats.SPEED, Stats.ATTACK},
			{"modest", Stats.SPECIAL_ATTACK, Stats.ATTACK},
			{"bold", Stats.DEFENCE, Stats.ATTACK},
			{"careful", Stats.SPECIAL_DEFENCE, Stats.SPECIAL_ATTACK},
			{"brave", Stats.ATTACK, Stats.SPEED}};
		for (Object[] n : natures) {
			Pokemon p = pokemon("eevee level=20 nature=" + n[0]);
			Component line = CobblemonProfessor.hints(p, false).get(0);
			Stats up = (Stats) n[1];
			Stats down = (Stats) n[2];
			helper.assertTrue(key(line).equals("message.aliveworkplace.professor.nature") && arg(line, 1).equals(up.getDisplayName()) && arg(line, 2).equals(down.getDisplayName()),
				n[0] + ": " + line.getString());
			String name = Character.toUpperCase(((String) n[0]).charAt(0)) + ((String) n[0]).substring(1);
			String want = name + " nature: raises " + up.getDisplayName().getString() + ", lowers " + down.getDisplayName().getString();
			helper.assertTrue(line.getString().equals(want), "\"" + line.getString() + "\", wanted \"" + want + "\"");
		}
		for (String neutral : List.of("hardy", "docile", "serious", "bashful", "quirky")) {
			Component line = CobblemonProfessor.hints(pokemon("eevee level=20 nature=" + neutral), false).get(0);
			helper.assertTrue(key(line).equals("message.aliveworkplace.professor.nature_neutral"), neutral + " isn't neutral: " + line.getString());
			helper.assertTrue(line.getString().endsWith(" nature: raises and lowers nothing"), "said: " + line.getString());
		}
		// Hidden ability, both ways, read as the player does
		Pokemon hidden = pokemon("pikachu level=5 ability=lightningrod");
		Pokemon plain = pokemon("pikachu level=5 ability=static");
		List<Component> h = CobblemonProfessor.hints(hidden, false);
		List<Component> n = CobblemonProfessor.hints(plain, false);
		helper.assertTrue(h.get(7).getString().equals("Ability Lightning Rod: its hidden ability!"), "hidden: " + h.get(7).getString());
		helper.assertTrue(n.get(7).getString().equals("Not its hidden ability (Lightning Rod): it has Static"), "not hidden: " + n.get(7).getString());
		helper.succeed();
	}

	/**
	 * The player's entry point: right-clicking the Professor with an empty hand opens the hints with the party's
	 * Pokémon (nature and IV words in the tooltip); with no Pokémon along, the screen says so.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_legends_rightclick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_legends_rightclick")
	public void rightClickOpensTheHints(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = make(helper, PokemonProfessor.ID, new BlockPos(13, 2, 13));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			InteractionResult empty = UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, prof, null);
			helper.assertTrue(empty.consumesAction(), "the right-click wasn't taken: " + empty);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "no hints screen: " + player.containerMenu);
			ItemStack none = ((ChoiceMenu) player.containerMenu).icon(CobblemonProfessor.FIRST_PARTY_SLOT);
			helper.assertTrue(none.getHoverName().getString().equals("You have no Pokémon with you"), "with an empty party: " + none.getHoverName().getString());
			player.closeContainer();
			Pokemon eevee = pokemon("eevee level=20 nature=timid");
			for (int i = 0; i < 6; i++) {
				eevee.getIvs().set(CobblemonProfessor.STATS.get(i), 31);
			}
			Cobblemon.INSTANCE.getStorage().getParty(player).add(eevee);
			UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, prof, null);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "no hints screen with a party: " + player.containerMenu);
			ItemStack icon = ((ChoiceMenu) player.containerMenu).icon(CobblemonProfessor.FIRST_PARTY_SLOT);
			var lore = icon.get(DataComponents.LORE);
			String text = lore == null ? "" : String.join(" / ", lore.lines().stream().map(Component::getString).toList());
			helper.assertTrue(text.contains("Timid nature: raises Speed") && text.contains("Best"), "Eevee's tooltip: " + text);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Ranger (29.22)

	/** The stand-in Alpha from both sides: a wild level-49 Pokémon isn't one (no Ranger), a level-50 one is. */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_level"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_level")
	public void standInAlphaIsLevel50Not49(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L); // past the morning (B142)
		BlockPos hall = helper.absolutePos(HALL);
		UUID ally = UUID.nameUUIDFromBytes(("qa-ally" + hall).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Reputation.add(level, hall, ally, "Ally", 50 - Reputation.points(level, hall, ally));
		PokemonEntity l49 = wild(helper, "machamp level=49", new BlockPos(22, 2, 22));
		helper.runAfterDelay(5, () -> {
			Legend ranger = legend(PokemonRanger.ID);
			long today = Chronicle.day(level);
			helper.assertTrue(!new AlphaNear(96).met(level, hall), "a wild level-49 Machamp counted as an Alpha");
			helper.assertTrue(!LegendGuests.candidates(level, hall, "hall", today).contains(ranger), "the Ranger may come for a level-49");
			l49.discard();
			wild(helper, "machamp level=50", new BlockPos(22, 2, 22));
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(new AlphaNear(96).met(level, hall), "a wild level-50 Machamp isn't the stand-in Alpha");
				helper.assertTrue(LegendGuests.candidates(level, hall, "hall", today).contains(ranger), "the Ranger doesn't come for a level-50");
				helper.succeed();
			});
		});
	}

	/**
	 * The reach from both sides, along one axis on the hall's floor: an Alpha 95 blocks from the hall counts, one 97
	 * blocks away doesn't; and a wild Pokémon 63 blocks away may be befriended, one 66 away may not.
	 */
	//$ gametest_ticks_batch AREA '900' '"qa_legends_reach"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa_legends_reach")
	public void alphaReach96AndBefriendReach64(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at95 = HALL.offset(95, 0, 0);
		BlockPos at97 = HALL.offset(97, 0, 0);
		BlockPos at63 = HALL.offset(0, 0, 63);
		BlockPos at66 = HALL.offset(0, 0, 66);
		forceChunks(helper, at95, at97, at63, at66);
		// A newly forced chunk takes a few ticks to take entities; until then addFreshEntity drops them unseen.
		whenReady(helper, 0, System.nanoTime(), () -> reach(helper, hall, at95, at97, at63, at66), at95, at97, at63, at66);
	}

	/**
	 * Runs {@code then} once every spot's chunk ticks entities and has loaded its own (B713: on CI a far chunk once
	 * wasn't ready within 200 ticks; its entities load off the server thread, so a busy runner gets up to 600, and the
	 * failure says what the chunk was still missing).
	 */
	private static void whenReady(GameTestHelper helper, int waited, long started, Runnable then, BlockPos... relative) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : relative) {
			BlockPos abs = helper.absolutePos(p);
			boolean ticking = level.isPositionEntityTicking(abs);
			boolean loaded = level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(abs));
			if (!ticking || !loaded) {
				helper.assertTrue(waited < 600, "the chunk at " + p + " never took entities (after " + waited + " ticks, "
					+ (System.nanoTime() - started) / 1_000_000L + " ms: entity ticking " + ticking + ", entities loaded " + loaded
					+ ", chunk present " + level.hasChunk(abs.getX() >> 4, abs.getZ() >> 4) + ")");
				helper.runAfterDelay(1, () -> whenReady(helper, waited + 1, started, then, relative));
				return;
			}
		}
		then.run();
	}

	private static void reach(GameTestHelper helper, BlockPos hall, BlockPos at95, BlockPos at97, BlockPos at63, BlockPos at66) {
		ServerLevel level = helper.getLevel();
		PokemonEntity near = pinned(wild(helper, "machamp level=60", at95));
		helper.assertTrue(level.getEntity(near.getUUID()) == near, "the Machamp 95 blocks out didn't spawn");
		helper.assertTrue(new AlphaNear(96).met(level, hall), "an Alpha 95 blocks from the hall isn't within 96 (it is "
			+ String.format("%.2f", Math.sqrt(near.distanceToSqr(hall.getCenter()))) + " away)");
		near.discard();
		PokemonEntity far = pinned(wild(helper, "machamp level=60", at97));
		PokemonEntity in = pinned(wild(helper, "rattata level=5", at63));
		PokemonEntity out = pinned(wild(helper, "meowth level=5", at66));
		for (PokemonEntity e : List.of(far, in, out)) {
			helper.assertTrue(level.getEntity(e.getUUID()) == e, e.getPokemon().getSpecies().getName() + " didn't spawn");
		}
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(far.isAlive() && in.isAlive() && out.isAlive(), "a Pokémon at the edge of reach died");
			helper.assertTrue(!new AlphaNear(96).met(level, hall), "an Alpha 97 blocks from the hall counted");
			List<net.minecraft.world.entity.Entity> befriendable = new CobblemonRanger().befriendable(level, hall, PokemonRanger.BEFRIEND_RADIUS);
			helper.assertTrue(befriendable.contains(in), "a wild Rattata 63 blocks away can't be befriended: " + befriendable + " (it is "
				+ String.format("%.2f", Math.sqrt(in.distanceToSqr(hall.getCenter()))) + " away)");
			helper.assertTrue(!befriendable.contains(out), "a wild Meowth 66 blocks away can be befriended");
			helper.succeed();
		});
	}

	/**
	 * Never a banned Pokémon, of every banned kind: mythical (Mew), Ultra Beast (Nihilego), paradox (Great Tusk) and
	 * legendary (Articuno) are banned, never befriendable, and befriending each is refused (it stays out, wild); a
	 * Rattata beside them is befriendable; a trainer's Pokémon never is.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_banned"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_banned")
	public void everyBannedKindIsNeverBefriended(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = owner(helper);
		String[] banned = {"mew", "nihilego", "greattusk", "articuno"};
		List<PokemonEntity> bans = new ArrayList<>();
		for (int i = 0; i < banned.length; i++) {
			bans.add(wild(helper, banned[i] + " level=10", new BlockPos(9 + i * 2, 2, 9)));
		}
		PokemonEntity rattata = wild(helper, "rattata level=5", new BlockPos(9, 2, 12));
		PokemonEntity trainers = wild(helper, "meowth level=5", new BlockPos(12, 2, 12));
		Cobblemon.INSTANCE.getStorage().getPC(player).add(trainers.getPokemon());
		Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(8, 2, 9));
		helper.runAfterDelay(5, () -> {
			WildPokemon w = new CobblemonRanger();
			List<net.minecraft.world.entity.Entity> befriendable = w.befriendable(level, hall, 64);
			for (PokemonEntity b : bans) {
				String name = b.getPokemon().getSpecies().getName();
				helper.assertTrue(w.banned(b), name + " isn't banned");
				helper.assertTrue(!befriendable.contains(b), name + " is befriendable");
			}
			helper.assertTrue(befriendable.contains(rattata), "a wild Rattata isn't befriendable: " + befriendable);
			helper.assertTrue(!befriendable.contains(trainers), "a trainer's Meowth is befriendable");
			for (PokemonEntity b : bans) {
				WildPokemon.Befriended result = PokemonRanger.befriend(level, hall, ranger, b, pasture);
				helper.assertTrue(result == WildPokemon.Befriended.REFUSED, b.getPokemon().getSpecies().getName() + " befriended: " + result);
				helper.assertTrue(b.isAlive(), b.getPokemon().getSpecies().getName() + " vanished on a refused befriending");
			}
			PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
			helper.assertTrue(block.getTetheredPokemon().isEmpty(), "a banned Pokémon went into the pasture");
			helper.succeed();
		});
	}

	/**
	 * None when the pastures are full: a pasture at its limit with the owner's Pokémon, a wild Pikachu beside the
	 * Ranger; the day passes with no walk, the Pikachu stays wild and out, and a befriending asked for anyway finds no room.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_legends_full"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_legends_full")
	public void noBefriendingIntoAFullPasture(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = owner(helper);
		PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
		int max = block.getMaxTethered();
		helper.assertTrue(max > 0 && max <= 40, "the pasture's limit: " + max);
		for (int i = 0; i < max; i++) {
			PastureCompatTests.pastured(helper, pasture, player, NINE_TYPES.get(i % NINE_TYPES.size()), SIDES[i % 4]);
		}
		PokemonEntity pikachu = wild(helper, "pikachu level=10", new BlockPos(8, 2, 10));
		Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(7, 2, 10));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(block.getTetheredPokemon().size() == max, "the pasture isn't full: " + block.getTetheredPokemon().size() + " of " + max);
			helper.assertTrue(new CobblemonRanger().pastureWithRoom(level, hall, player.getUUID()).isEmpty(), "a full pasture has room");
			for (int i = 0; i < 3; i++) {
				PokemonRanger.tick(ranger);
			}
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "the Ranger walks for a full pasture: " + PokemonRanger.state(ranger));
			helper.assertTrue(pikachu.isAlive() && pikachu.getPokemon().getOwnerUUID() == null, "the Pikachu was taken with the pasture full");
			helper.assertTrue(block.getTetheredPokemon().size() == max, "the full pasture took one more");
			helper.assertTrue(PokemonRanger.befriend(level, hall, ranger, pikachu, pasture) == WildPokemon.Befriended.NO_ROOM, "befriended into a full pasture");
			helper.assertTrue(pikachu.isAlive(), "the Pikachu vanished when there was no room");
			helper.assertTrue(hall(helper).chronicle().stream().noneMatch(e -> e.text().getString().contains("befriended")), "the chronicle tells of a befriending");
			helper.succeed();
		});
	}

	/**
	 * Calming is per Alpha: with two Alphas near, the Ranger calms the nearest; the other still hurts a villager, and is
	 * the one left to calm. The calm line reads as the spec says (for good, in the village).
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_calm"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_calm")
	public void calmingOneAlphaLeavesTheOther(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 2000L); // the morning
		PokemonEntity nearAlpha = wild(helper, "machamp level=60", new BlockPos(18, 2, 18));
		PokemonEntity farAlpha = wild(helper, "golem level=60", new BlockPos(26, 2, 26));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 21));
		villager.setNoAi(true);
		Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(19, 2, 17));
		helper.runAfterDelay(5, () -> {
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.calmed(nearAlpha), "the Alpha beside the Ranger wasn't calmed");
			helper.assertTrue(!PokemonRanger.calmed(farAlpha), "an Alpha the Ranger never reached was calmed");
			float before = villager.getHealth();
			villager.hurt(level.damageSources().mobAttack(farAlpha), 2f);
			helper.assertTrue(villager.getHealth() < before, "the Alpha not calmed can't hurt a villager");
			villager.setHealth(villager.getMaxHealth());
			float full = villager.getHealth();
			villager.hurt(level.damageSources().mobAttack(nearAlpha), 4f);
			helper.assertTrue(villager.getHealth() == full, "the calmed Alpha hurt a villager");
			helper.assertTrue(PokemonRanger.alphaToCalm(level, helper.absolutePos(HALL), ranger).orElse(null) == farAlpha, "the other Alpha isn't the one left to calm");
			String line = Component.translatable("message.aliveworkplace.ranger.calmed", "Ranger Wren", "Machamp", "Oakvale").getString();
			helper.assertTrue(line.equals("Ranger Wren calmed the wild Machamp near Oakvale: it will never attack anyone in the village again."), "line: " + line);
			helper.succeed();
		});
	}
}
