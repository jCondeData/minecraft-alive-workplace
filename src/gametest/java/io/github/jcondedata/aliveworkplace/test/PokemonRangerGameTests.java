package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.io.Reader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.22 without Cobblemon: the Pokémon Ranger's file doesn't load and nothing errors (with Cobblemon's requirement
 * taken out, it reads with both powers and the {@code alpha_near} condition), no wild Pokémon are found, {@code alpha_near}
 * reads and is never met, and a Ranger's saved day reads back (an old villager without one starts empty).
 */
public class PokemonRangerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutCobblemonTheRangerNeverComes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon"), "Cobblemon is installed in the plain suite");
		helper.assertTrue(!WildPokemon.EXTENSION.present(), "the wild Pokémon are filled without Cobblemon");
		helper.assertTrue(Legends.get(PokemonRanger.ID).isEmpty(), "the Ranger loaded without Cobblemon");
		JsonObject json;
		try (Reader reader = level.getServer().getResourceManager().getResourceOrThrow(AliveWorkplace.id("legends/pokemon_ranger.json")).openAsReader()) {
			json = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new AssertionError("pokemon_ranger.json isn't in the jar", e);
		}
		helper.assertTrue(Legends.read(PokemonRanger.ID, json) == null, "the file read as a Legend without Cobblemon");
		JsonObject unguarded = json.deepCopy();
		unguarded.remove("requires");
		Legend ranger = Legends.read(PokemonRanger.ID, unguarded);
		helper.assertTrue(ranger != null && ranger.powers(PokemonRanger.CalmPower.class).size() == 1 && ranger.powers(PokemonRanger.BefriendPower.class).size() == 1,
			"the file's powers don't read");
		helper.assertTrue(ranger.powers(PokemonRanger.CalmPower.class).get(0).describe().getString().startsWith("Each morning walks to an Alpha"), "the calm power's line");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Condition condition = Conditions.parse(JsonParser.parseString("{\"type\": \"alpha_near\"}").getAsJsonObject());
		Condition.Progress progress = condition.progress(level, hall);
		helper.assertTrue(!progress.met() && progress.need() == 1, "an Alpha without Cobblemon");
		helper.assertTrue(progress.line().getString().equals("An Alpha Pokémon (or a wild one of level 50 or more) within 96 blocks of the hall: 0 of 1"),
			"line: " + progress.line().getString());
		helper.assertTrue(WildPokemon.alphasNear(level, hall, 96).isEmpty(), "Alphas without Cobblemon");
		// Nothing a villager does with it errors, and an ordinary mob's blow isn't stopped
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		villager.setNoAi(true);
		PokemonRanger.tick(villager);
		helper.assertTrue(!ModAttachments.RANGER.has(villager), "an ordinary villager got a Ranger's day");
		helper.assertTrue(PokemonRanger.alphaToCalm(level, hall, villager).isEmpty(), "an Alpha to calm without Cobblemon");
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		helper.assertTrue(PokemonRanger.allowDamage(villager, level.damageSources().mobAttack(zombie), 2f), "a zombie's blow was stopped");
		ModAttachments.RANGER_CALMED.set(zombie, true);
		helper.assertTrue(!PokemonRanger.allowDamage(villager, level.damageSources().mobAttack(zombie), 2f), "a calmed attacker's blow in the village went through");
		zombie.discard();
		// The Ranger's day saves; an old villager (no "ranger") reads as empty
		PokemonRanger.State empty = PokemonRanger.State.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		helper.assertTrue(empty.equals(PokemonRanger.State.EMPTY), "an empty save: " + empty);
		PokemonRanger.State day = new PokemonRanger.State(4, 5, PokemonRanger.LEAD, java.util.Optional.of(villager.getUUID()), java.util.Optional.of(hall), 77);
		PokemonRanger.State back = PokemonRanger.State.CODEC.parse(JsonOps.INSTANCE, PokemonRanger.State.CODEC.encodeStart(JsonOps.INSTANCE, day).getOrThrow()).getOrThrow();
		helper.assertTrue(back.equals(day), "after a save: " + back);
		villager.discard();
		level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}
}
