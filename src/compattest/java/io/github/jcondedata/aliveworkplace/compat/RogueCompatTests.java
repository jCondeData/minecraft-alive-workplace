package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.RoguePokemon;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 31.14 with the real Cobblemon: a rogue's species comes from its biome's group, its level from the village's
 * rank (35, 50, 65); it's named "Rogue Ursaring", half again as big (or an Alpha on Cobblemon 1.8), and glows for its
 * bounty's takers nearby; a win and a capture each pay once (a catcher keeps it, name and all).
 */
public class RogueCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(18, 2, 18);

	private static BlockPos village(GameTestHelper helper, VillageRanks.Rank rank) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Rogues.ENABLED = true;
		Bounties.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(rank);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.forget(level);
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static PokemonEntity spawn(GameTestHelper helper, Bounties.Bounty b) {
		Entity e = Rogues.spawn(helper.getLevel(), b, null);
		helper.assertTrue(e instanceof PokemonEntity, "no rogue: " + e);
		PokemonEntity rogue = (PokemonEntity) e;
		rogue.setNoAi(true);
		return rogue;
	}

	/** What the player has been paid: CobbleDollars where that mod keeps the money, else emeralds (as dollars). */
	private static long money(ServerPlayer player) {
		long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), 0L);
		return dollars + (long) player.getInventory().countItem(Items.EMERALD) * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD;
	}

	/**
	 * Cobblemon has every species of the eight groups; a forest posts an Ursaring, Scyther or Pinsir; a rogue is the
	 * species posted, at level 35 in a Village, 50 in a Town and 65 in a City, named "Rogue Ursaring", wild, half again
	 * as big (an Alpha on 1.8), and stays a rogue through a save.
	 */
	//$ gametest_ticks_batch AREA '200' '"rogue_species"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "rogue_species")
	public void speciesLevelAndNameByBiomeAndRank(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RoguePokemon.present() && Rogues.available(), "rogues aren't available with Cobblemon");
		for (Rogues.Group g : Rogues.groups().values()) {
			helper.assertTrue(Rogues.species(g).size() == 3, g.id() + ": Cobblemon has only " + Rogues.species(g));
		}
		var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		Map<ResourceKey<Biome>, List<String>> by = Map.of(Biomes.FOREST, List.of("ursaring", "scyther", "pinsir"),
			Biomes.SNOWY_PLAINS, List.of("mamoswine", "abomasnow", "beartic"), Biomes.STONY_SHORE, List.of("kingler", "crawdaunt", "barbaracle"));
		by.forEach((biome, species) -> {
			List<String> got = Rogues.species(Rogues.group(biomes.getHolderOrThrow(biome))).stream().map(ResourceLocation::getPath).toList();
			helper.assertTrue(got.equals(species), biome.location() + ": " + got);
		});
		ResourceLocation ursaring = ResourceLocation.parse("cobblemon:ursaring");
		helper.assertTrue(RoguePokemon.nameOf(ursaring).getString().equals("Ursaring"), "the species' name: " + RoguePokemon.nameOf(ursaring).getString());
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		int[] want = {35, 50, 65};
		VillageRanks.Rank[] ranks = {VillageRanks.Rank.VILLAGE, VillageRanks.Rank.TOWN, VillageRanks.Rank.CITY};
		for (int i = 0; i < 3; i++) {
			entity.setRank(ranks[i]);
			Bounties.forget(level);
			Bounties.Bounty b = Rogues.post(level, hall, ursaring, helper.absolutePos(SPOT));
			helper.assertTrue(b.rogueLevel == want[i], ranks[i] + ": level " + b.rogueLevel);
			helper.assertTrue(b.reward == Math.round(Rogues.REWARD * VillageRanks.questRewardFactor(ranks[i])), ranks[i] + ": reward " + b.reward);
			PokemonEntity rogue = spawn(helper, b);
			Pokemon p = rogue.getPokemon();
			helper.assertTrue(p.getSpecies().getResourceIdentifier().equals(ursaring), "species: " + p.getSpecies().getResourceIdentifier());
			helper.assertTrue(p.getLevel() == want[i], ranks[i] + ": spawned at level " + p.getLevel());
			helper.assertTrue(p.isWild() && rogue.getOwnerUUID() == null, "the rogue isn't wild");
			helper.assertTrue(rogue.getPokemon().getDisplayName(false).getString().equals("Rogue Ursaring"), "its name: " + p.getDisplayName(false).getString());
			helper.assertTrue(b.name(level).getString().equals("Rogue Ursaring"), "the poster's name: " + b.name(level).getString());
			helper.assertTrue(rogue.getTags().contains(Rogues.TAG) && b.captain.equals(rogue.getUUID()), "the rogue isn't the bounty's");
			if (PokemonFeatures.ALPHAS.available()) {
				boolean alpha;
				try {
					alpha = (Boolean) Pokemon.class.getMethod("isAlpha").invoke(p); // 1.8's API; this suite compiles against 1.7.3
				} catch (ReflectiveOperationException e) {
					alpha = false;
				}
				helper.assertTrue(alpha, "on Cobblemon 1.8 the rogue should be an Alpha");
			} else {
				helper.assertTrue(CobblemonRogues.size(rogue) == 1.5f, "size: " + CobblemonRogues.size(rogue));
			}
			helper.assertTrue(CobblemonRogues.bountyOf(p) != null && CobblemonRogues.bountyOf(p).equals(b.id), "the rogue doesn't carry its bounty");
			// Through a save: still the bounty's rogue
			CompoundTag tag = rogue.saveWithoutId(new CompoundTag());
			PokemonEntity loaded = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("ursaring", " ", "=").createEntity(level);
			loaded.load(tag);
			helper.assertTrue(b.id.equals(CobblemonRogues.bountyOf(loaded.getPokemon())), "the bounty was lost in a save");
			loaded.discard();
			rogue.discard();
		}
		// The hall's round with Cobblemon posts a rogue among its bounties (a forest's or another group's species).
		entity.setRank(VillageRanks.Rank.VILLAGE);
		Bounties.forget(level);
		Bounties.Bounty any = Rogues.post(level, hall, RandomSource.create(5));
		helper.assertTrue(any == null || (any.rogue != null && Rogues.groups().values().stream().anyMatch(g -> g.species().contains(any.rogue))),
			"a rogue of no group: " + (any == null ? null : any.rogue));
		helper.succeed();
	}

	/** A win pays the winner once: a second report of the same win pays nothing; the chronicle says who. */
	//$ gametest_ticks_batch AREA '200' '"rogue_win"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "rogue_win")
	public void aWinPaysOnce(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer player = player(helper);
		long before = money(player);
		helper.assertTrue(CobblemonRogues.beaten(level.getServer(), rogue.getPokemon(), player, false), "the win didn't pay");
		helper.assertTrue(money(player) - before == (long) b.reward * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD,
			"paid " + (money(player) - before) + ", not " + b.reward + " emeralds' worth");
		helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), rogue.getPokemon(), player, false), "the win paid twice");
		helper.assertTrue(money(player) - before == (long) b.reward * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD, "paid twice");
		helper.assertTrue(Bounties.open(level, hall).isEmpty() && Bounties.Data.get(level).claimed.contains(b.id), "the bounty is still up");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().contains("beat Rogue Tauros")),
			"no chronicle line for the win");
		helper.succeed();
	}

	/** A capture pays the catcher once and they keep the rogue, its name with it; it carries no bounty any more. */
	//$ gametest_ticks_batch AREA '200' '"rogue_catch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "rogue_catch")
	public void aCapturePaysOnceAndTheCatcherKeepsIt(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.TOWN);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:onix"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer player = player(helper);
		Pokemon pokemon = rogue.getPokemon();
		rogue.discard();
		com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
		long before = money(player);
		helper.assertTrue(CobblemonRogues.beaten(level.getServer(), pokemon, player, true), "the capture didn't pay");
		helper.assertTrue(!CobblemonRogues.beaten(level.getServer(), pokemon, player, true), "the capture paid twice");
		helper.assertTrue(money(player) - before == (long) b.reward * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD,
			"paid " + (money(player) - before));
		helper.assertTrue(b.reward == 24, "16 emeralds times the Town factor: " + b.reward);
		helper.assertTrue(CobblemonRogues.bountyOf(pokemon) == null, "the caught rogue still carries its bounty");
		helper.assertTrue(pokemon.getOwnerUUID() != null && pokemon.getOwnerUUID().equals(player.getUUID()), "the catcher doesn't keep it");
		helper.assertTrue(pokemon.getDisplayName(false).getString().equals("Rogue Onix"), "its name: " + pokemon.getDisplayName(false).getString());
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "the bounty is still up");
		helper.succeed();
	}

	/** The rogue glows for a player who took its bounty within 32 blocks, not for one who didn't, and not from farther. */
	//$ gametest_ticks_batch AREA '200' '"rogue_glow"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "rogue_glow")
	public void glowsForItsTakersNearby(GameTestHelper helper) {
		BlockPos hall = village(helper, VillageRanks.Rank.VILLAGE);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:scyther"), helper.absolutePos(SPOT));
		PokemonEntity rogue = spawn(helper, b);
		ServerPlayer taker = player(helper);
		ServerPlayer other = player(helper);
		taker.setPos(helper.absoluteVec(new Vec3(14.5, 2, 14.5)));
		other.setPos(helper.absoluteVec(new Vec3(15.5, 2, 15.5)));
		Bounties.take(taker, level, b);
		Rogues.glow(level);
		helper.assertTrue(Rogues.glowingFor(b).equals(java.util.Set.of(taker.getUUID())), "glows for " + Rogues.glowingFor(b));
		helper.assertTrue(!rogue.hasGlowingTag(), "the rogue glows for everyone");
		taker.setPos(rogue.getX() + 40, rogue.getY(), rogue.getZ());
		Rogues.glow(level);
		helper.assertTrue(Rogues.glowingFor(b).isEmpty(), "still glows 40 blocks away: " + Rogues.glowingFor(b));
		helper.succeed();
	}
}
