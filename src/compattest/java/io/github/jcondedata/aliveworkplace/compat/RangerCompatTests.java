package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.mark.Marks;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRanger;
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
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.AlphaNear;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 29.22 with the real Cobblemon: the Pokémon Ranger comes only while an Alpha (the Alpha mark, or a wild
 * Pokémon of level 50 or more) is near the hall; a calmed Alpha never hurts a villager or a player in the village, and
 * stays calmed through a save; a befriended Pokémon is the hall owner's, in the village's pasture; a banned species
 * (legendary, mythical, Ultra Beast, paradox) never is, and nothing is befriended without an owner or a pasture.
 */
public class RangerCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos PASTURE = new BlockPos(6, 2, 6);

	/** A hall in the middle of the area (radius 14), Legend needs off, every Pokémon within 100 blocks gone, all put back afterwards. */
	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(100)).forEach(e -> e.discard());
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
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
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

	private static Legend legend() {
		return Legends.get(PokemonRanger.ID).orElseThrow(() -> new AssertionError("pokemon_ranger.json didn't load with Cobblemon"));
	}

	private static Villager ranger(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend(), "test");
		LegendPowers.forget();
		return v;
	}

	/** A wild Pokémon standing still at {@code at}. */
	private static PokemonEntity wild(GameTestHelper helper, String properties, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse(properties, " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	private static ServerPlayer owner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		hall(helper).setOwner(player.getUUID(), player.getGameProfile().getName());
		return player;
	}

	/**
	 * The Ranger's file: Rare, a hall guest 1 morning in 3, likes clothes; they're a candidate only while an Alpha is
	 * within 96 blocks: not for a wild level-10 Pikachu, yes for a level-55 one (the stand-in) and for one with the
	 * Alpha mark; a trainer's Pokémon doesn't count.
	 */
	//$ gametest_ticks_batch AREA '400' '"ranger_comes"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "ranger_comes")
	public void rangerComesOnlyWithAnAlphaNear(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Legend ranger = legend();
		helper.assertTrue(ranger.rarity().name().equals("RARE"), "rarity: " + ranger.rarity());
		helper.assertTrue(ranger.conditions().size() == 1 && ranger.conditions().get(0) instanceof AlphaNear a && a.radius() == 96, "conditions: " + ranger.conditions());
		List<com.google.gson.JsonObject> ways = ranger.ways("visit");
		helper.assertTrue(ways.size() == 1 && ways.get(0).get("place").getAsString().equals("hall"), "ways: " + ways);
		float chance = LegendGuests.chance(level, hall, ways.get(0));
		helper.assertTrue(Math.abs(chance - 1f / 3f) < 0.001f, "chance: " + chance);
		wild(helper, "pikachu level=10", new BlockPos(20, 2, 20));
		helper.runAfterDelay(5, () -> {
			long today = Chronicle.day(level);
			Condition.Progress none = new AlphaNear(96).progress(level, hall);
			helper.assertTrue(!none.met(), "a level-10 Pikachu is no Alpha");
			helper.assertTrue(none.line().getString().equals("An Alpha Pokémon (or a wild one of level 50 or more) within 96 blocks of the hall: 0 of 1"),
				"line: " + none.line().getString());
			helper.assertTrue(!LegendGuests.candidates(level, hall, "hall", today).contains(ranger), "the Ranger may come without an Alpha");
			PokemonEntity big = wild(helper, "pikachu level=55", new BlockPos(24, 2, 8));
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(new AlphaNear(96).met(level, hall), "a level-55 Pikachu is the stand-in Alpha");
				helper.assertTrue(LegendGuests.candidates(level, hall, "hall", today).contains(ranger), "the Ranger doesn't come with an Alpha near");
				big.discard();
				PokemonEntity marked = wild(helper, "eevee level=12", new BlockPos(8, 2, 24));
				marked.getPokemon().addPotentialMark(Marks.getByIdentifier(CobblemonRanger.ALPHA_MARK));
				ServerPlayer player = helper.makeMockServerPlayerInLevel();
				PokemonEntity owned = wild(helper, "snorlax level=70", new BlockPos(24, 2, 24));
				com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(owned.getPokemon());
				helper.runAfterDelay(5, () -> {
					List<net.minecraft.world.entity.Entity> alphas = WildPokemon.alphasNear(level, hall, 96);
					helper.assertTrue(alphas.size() == 1 && alphas.get(0) == marked, "Alphas: " + alphas + " (the marked Eevee, not the trainer's Snorlax)");
					helper.assertTrue(LegendGuests.candidates(level, hall, "hall", today).contains(ranger), "the Alpha mark doesn't bring the Ranger");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A wild Alpha hurts a villager and a player in the village; once the Ranger, walking up to it in the morning, is
	 * beside it, it's calmed and never does again, even after a save and reload.
	 */
	//$ gametest_ticks_batch AREA '400' '"ranger_calm"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "ranger_calm")
	public void calmedAlphaNeverAttacksAVillager(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 2000L); // the morning
		PokemonEntity alpha = wild(helper, "machamp level=60", new BlockPos(18, 2, 18));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 20));
		villager.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(19.5, 2, 19.5)));
		Villager ranger = ranger(helper, new BlockPos(19, 2, 17));
		helper.runAfterDelay(5, () -> {
			float before = villager.getHealth();
			villager.hurt(level.damageSources().mobAttack(alpha), 2f);
			helper.assertTrue(villager.getHealth() < before, "an Alpha not yet calmed should hurt the villager (the control)");
			villager.setHealth(villager.getMaxHealth());
			// a player just logged in can't be hurt for a few seconds, so the damage gate itself is asked for them
			helper.assertTrue(PokemonRanger.allowDamage(player, level.damageSources().mobAttack(alpha), 2f), "an Alpha not yet calmed may hurt a player (the control)");
			helper.assertTrue(PokemonRanger.alphaToCalm(level, helper.absolutePos(HALL), ranger).orElse(null) == alpha, "the Ranger doesn't see the Alpha");
			PokemonRanger.tick(ranger); // chooses the Alpha, and is beside it: calmed
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.calmed(alpha), "the Ranger beside it didn't calm the Alpha");
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE && PokemonRanger.state(ranger).calmDay() == Chronicle.day(level),
				"the Ranger's day: " + PokemonRanger.state(ranger));
			float full = villager.getHealth();
			helper.assertTrue(!villager.hurt(level.damageSources().mobAttack(alpha), 4f) && villager.getHealth() == full, "a calmed Alpha hurt a villager");
			helper.assertTrue(!PokemonRanger.allowDamage(player, level.damageSources().mobAttack(alpha), 4f), "a calmed Alpha may hurt a player in the village");
			// calmed for good: through a save
			CompoundTag tag = alpha.saveWithoutId(new CompoundTag());
			PokemonEntity loaded = PokemonProperties.Companion.parse("machamp level=60", " ", "=").createEntity(level);
			loaded.load(tag);
			helper.assertTrue(PokemonRanger.calmed(loaded), "the calm was lost in a save");
			helper.assertTrue(PokemonRanger.alphaToCalm(level, helper.absolutePos(HALL), ranger).isEmpty(), "a calmed Alpha is calmed again");
			helper.succeed();
		});
	}

	/**
	 * The day's befriending through the Ranger's own walk: beside a wild Pikachu, they lead it to the Pasture Block, where
	 * it's the hall owner's Pokémon, tethered there, with a line in the chronicle; the wild one is gone.
	 */
	//$ gametest_ticks_batch AREA '400' '"ranger_befriend"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "ranger_befriend")
	public void befriendedPokemonBelongsToTheOwnerInThePasture(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L); // past the morning: no calming first
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = owner(helper);
		PokemonEntity pikachu = wild(helper, "pikachu level=10", new BlockPos(8, 2, 9));
		Villager ranger = ranger(helper, new BlockPos(7, 2, 9));
		helper.runAfterDelay(5, () -> {
			UUID wildId = pikachu.getUUID();
			PokemonRanger.tick(ranger); // the day's choice: the Pikachu, already beside → leading it
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.LEAD, "not leading: " + PokemonRanger.state(ranger));
			PokemonRanger.tick(ranger); // at the pasture: befriended
			PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
			var tethered = block.getTetheredPokemon();
			helper.assertTrue(tethered.size() == 1, "tethered: " + tethered.size());
			var t = tethered.get(0);
			helper.assertTrue(t.getPlayerId().equals(player.getUUID()), "the pasture's Pokémon isn't the owner's");
			helper.assertTrue(t.getPokemon() != null && t.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("pikachu")
				&& player.getUUID().equals(t.getPokemon().getOwnerUUID()), "the Pikachu isn't the owner's");
			helper.assertTrue(level.getEntity(wildId) == null || !level.getEntity(wildId).isAlive(), "the wild Pikachu is still out");
			helper.assertTrue(PokemonRanger.state(ranger).befriendDay() == Chronicle.day(level) && PokemonRanger.state(ranger).task() == PokemonRanger.NONE,
				"the day: " + PokemonRanger.state(ranger));
			var chronicle = hall(helper).chronicle();
			helper.assertTrue(chronicle.stream().anyMatch(e -> e.text().getString().contains("befriended a wild Pikachu and led it to the village's pasture")),
				"no chronicle line");
			// once a day: another wild one the same day isn't befriended
			wild(helper, "bulbasaur level=10", new BlockPos(8, 2, 8));
			helper.runAfterDelay(3, () -> {
				PokemonRanger.tick(ranger);
				helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a second befriending today");
				helper.succeed();
			});
		});
	}

	/**
	 * Never a banned species: a wild Mewtwo isn't chosen, and befriending it is refused; and none at all when the hall has
	 * no owner, or its only pasture is someone else's.
	 */
	//$ gametest_ticks_batch AREA '400' '"ranger_banned"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "ranger_banned")
	public void bannedSpeciesNeverBefriended(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L);
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		BlockPos hall = helper.absolutePos(HALL);
		PokemonEntity mewtwo = wild(helper, "mewtwo level=10", new BlockPos(8, 2, 9));
		Villager ranger = ranger(helper, new BlockPos(7, 2, 9));
		helper.runAfterDelay(5, () -> {
			// no owner: nothing all day
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "befriending without an owner");
			ServerPlayer player = owner(helper);
			WildPokemon w = new CobblemonRanger();
			helper.assertTrue(w.banned(mewtwo), "Mewtwo isn't banned");
			helper.assertTrue(!w.befriendable(level, hall, 64).contains(mewtwo), "Mewtwo is befriendable");
			helper.assertTrue(PokemonRanger.befriend(level, hall, ranger, mewtwo, pasture) == WildPokemon.Befriended.REFUSED, "Mewtwo befriended");
			PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
			helper.assertTrue(block.getTetheredPokemon().isEmpty() && mewtwo.isAlive(), "Mewtwo went into the pasture");
			// a pasture of someone else's isn't the village's to fill
			helper.assertTrue(w.pastureWithRoom(level, hall, player.getUUID()).isPresent(), "the pasture should have room for the owner");
			block.setOwnerId(UUID.randomUUID());
			helper.assertTrue(w.pastureWithRoom(level, hall, player.getUUID()).isEmpty(), "someone else's pasture was used");
			helper.succeed();
		});
	}
}
