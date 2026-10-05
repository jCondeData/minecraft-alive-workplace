package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/** ROADMAP 28.12 with the real Cobblemon (and Cobbreeding): the Daycare Keeper's pairs, dawns and eggs. */
public class DaycareKeeperCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	private static BlockState half(String part) {
		Block pasture = BuiltInRegistries.BLOCK.get(c("pasture"));
		BlockState state = pasture.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("part")) {
				return with(state, property, part);
			}
		}
		throw new IllegalStateException("cobblemon:pasture has no part property");
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow());
	}

	static Villager keeper(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(PASTURE), ModVillagers.PASTURE_POI, ModVillagers.DAYCARE_KEEPER);
		helper.assertTrue(DaycareKeepers.isKeeper(villager), "not a daycare keeper");
		return villager;
	}

	static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static ServerPlayer trainer(GameTestHelper helper, Pokemon... pokemon) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		for (Pokemon p : pokemon) {
			party.add(p);
		}
		return player;
	}

	/** Moves the pair's last look {@code days} days back: that many dawns have passed. */
	private static void daysPass(Villager keeper, int days) {
		List<DaycareKeepers.Pair> pairs = new ArrayList<>();
		for (DaycareKeepers.Pair pair : DaycareKeepers.pairs(keeper)) {
			pairs.add(pair.withEggs(pair.eggs(), pair.day() - days));
		}
		DaycareKeepers.setPairs(keeper, pairs);
	}

	private static int eggs(Villager keeper) {
		return DaycareKeepers.pairs(keeper).stream().mapToInt(DaycareKeepers.Pair::eggs).sum();
	}

	/**
	 * A compatible pair (two Eevee, mother and father): left from the screen's party row, they get along well; a forced
	 * dawn finds an egg; the hatchling is a level-1 Eevee with at least 3 IVs from its parents, the nature of the parent
	 * holding an Everstone and its mother's ball.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aCompatiblePairGivesAnEggWithInheritance(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon mother = make("eevee gender=female level=20 nature=timid pokeball=great_ball");
		Pokemon father = make("eevee gender=male level=20 nature=adamant");
		for (Stats stat : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
			mother.getIvs().set(stat, 31);
			father.getIvs().set(stat, 30);
		}
		mother.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(c("everstone"))), false, false);
		ServerPlayer player = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(mother, father) == DaycareKeepers.WELL, "two Eevee get along " + CobblemonDaycareKeeper.getAlong(mother, father));
		// The screen: click the mother, then the father (party slots 2 and 3, after the Bulbasaur)
		ChoiceMenu menu = CobblemonDaycareKeeper.menuForTest(player, keeper);
		menu.press(CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1, player);
		menu.press(CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2, player);
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 1, "no pair left from the screen: " + DaycareKeepers.pairs(keeper));
		helper.assertTrue(!menu.icon(CobblemonDaycareKeeper.FIRST).isEmpty() && !menu.icon(CobblemonDaycareKeeper.GET_ALONG).isEmpty(),
			"the screen doesn't show the pair and how well they get along");
		helper.assertTrue(menu.icon(CobblemonDaycareKeeper.GET_ALONG).getHoverName().getString().equals("well"),
			"it says they get along " + menu.icon(CobblemonDaycareKeeper.GET_ALONG).getHoverName().getString());
		// Dawn: 50% at Novice, three tries from a fixed seed
		RandomSource random = RandomSource.create(7);
		for (int day = 0; day < 10 && eggs(keeper) == 0; day++) {
			daysPass(keeper, 1);
			DaycareKeepers.dawn(helper.getLevel(), keeper, random);
		}
		helper.assertTrue(eggs(keeper) > 0, "ten dawns and no egg");
		DaycareKeepers.Pair pair = DaycareKeepers.pairs(keeper).get(0);
		Pokemon a = CobblemonDaycareKeeper.load(helper.getLevel(), pair.first());
		Pokemon b = CobblemonDaycareKeeper.load(helper.getLevel(), pair.second());
		CobblemonDaycareKeeper.giveEgg(player, a, b, RandomSource.create(3), false);
		Pokemon baby = null;
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			if (p.getLevel() == 1) {
				baby = p;
			}
		}
		helper.assertTrue(baby != null, "no hatchling in the party");
		helper.assertTrue(baby.getSpecies().getResourceIdentifier().getPath().equals("eevee"), "hatched a " + baby.getSpecies().getResourceIdentifier());
		helper.assertTrue(baby.getNature() == mother.getNature(), "nature " + baby.getNature().getName() + ", not the Everstone's " + mother.getNature().getName());
		int inherited = 0;
		for (Stats stat : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
			int iv = baby.getIvs().getOrDefault(stat);
			inherited += iv == 30 || iv == 31 ? 1 : 0;
		}
		helper.assertTrue(inherited >= 3, "only " + inherited + " IVs from the parents");
		helper.assertTrue(baby.getCaughtBall() == mother.getCaughtBall(), "in a " + baby.getCaughtBall().getName() + ", not the mother's ball");
		helper.succeed();
	}

	/** Ditto's hatchling is the other parent's base form: a Ditto and a Vaporeon hatch an Eevee. A Destiny Knot passes 5 IVs. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void dittoGivesTheBaseFormOfTheOther(GameTestHelper helper) {
		Pokemon ditto = make("ditto level=30");
		Pokemon vaporeon = make("vaporeon gender=male level=30");
		for (Stats stat : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
			ditto.getIvs().set(stat, 31);
			vaporeon.getIvs().set(stat, 31);
		}
		ditto.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(c("destiny_knot"))), false, false);
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(ditto, vaporeon) == DaycareKeepers.SO_SO, "Ditto gets along " + CobblemonDaycareKeeper.getAlong(ditto, vaporeon));
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(ditto, make("ditto level=5")) == DaycareKeepers.NOT_AT_ALL, "two Ditto get along");
		Pokemon baby = CobblemonDaycareKeeper.hatchling(ditto, vaporeon, RandomSource.create(1));
		helper.assertTrue(baby.getSpecies().getResourceIdentifier().getPath().equals("eevee") && baby.getLevel() == 1,
			"a Ditto and a Vaporeon hatched a level " + baby.getLevel() + " " + baby.getSpecies().getResourceIdentifier());
		int perfect = 0;
		for (Stats stat : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
			perfect += baby.getIvs().getOrDefault(stat) == 31 ? 1 : 0;
		}
		helper.assertTrue(perfect >= 5, "a Destiny Knot passed only " + perfect + " IVs");
		helper.succeed();
	}

	/** An incompatible pair (no egg group in common; or the Undiscovered group) never finds an egg, however many dawns. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void anIncompatiblePairNeverGivesAnEgg(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon eevee = make("eevee gender=male level=20");
		Pokemon magikarp = make("magikarp gender=female level=20");
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(eevee, magikarp) == DaycareKeepers.NOT_AT_ALL, "Eevee and Magikarp get along");
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(make("pichu gender=male"), make("pichu gender=female")) == DaycareKeepers.NOT_AT_ALL,
			"two Pichu (Undiscovered) get along");
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(make("eevee gender=male"), make("eevee gender=male")) == DaycareKeepers.NOT_AT_ALL,
			"two fathers get along");
		ServerPlayer player = trainer(helper, eevee, magikarp);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, eevee, magikarp), "the pair wasn't left");
		RandomSource random = RandomSource.create(11);
		for (int day = 0; day < 30; day++) {
			daysPass(keeper, 1);
			DaycareKeepers.dawn(helper.getLevel(), keeper, random);
		}
		helper.assertTrue(eggs(keeper) == 0, eggs(keeper) + " eggs from a pair that doesn't get along");
		helper.succeed();
	}

	/** With Cobbreeding installed, collecting an egg (4 emeralds' worth) gives a real Cobbreeding egg item; without the money, nothing. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void collectingGivesACobbreedingEgg(GameTestHelper helper) {
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "Cobbreeding isn't loaded in the compat tests");
		Villager keeper = keeper(helper);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer player = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "the pair wasn't left");
		DaycareKeepers.setPairs(keeper, List.of(DaycareKeepers.pairs(keeper).get(0).withEggs(1, DaycareKeepers.day(helper.getLevel()))));
		CobbleDollarsBank.take(player, CobbleDollarsBank.balance(player));
		CobblemonDaycareKeeper.collect(player, keeper);
		helper.assertTrue(eggs(keeper) == 1 && CobblemonDaycareKeeper.countEggs(player) == 0, "collected without paying");
		CobbleDollarsBank.add(player, 1000);
		CobblemonDaycareKeeper.collect(player, keeper);
		helper.assertTrue(eggs(keeper) == 0, "the egg is still waiting");
		helper.assertTrue(CobblemonDaycareKeeper.countEggs(player) == 1, "no Cobbreeding egg in the inventory");
		helper.assertTrue(CobbleDollarsBank.balance(player) == 1000 - DaycareKeepers.EGG_PRICE * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD,
			"paid " + (1000 - CobbleDollarsBank.balance(player)));
		helper.succeed();
	}

	/** If she dies, both Pokémon of every pair go to their trainer's PC. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theKeepersDeathSendsThePairToThePc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer player = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "the pair wasn't left");
		UUID a = mother.getUuid();
		UUID b = father.getUuid();
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			boolean hasA = false, hasB = false;
			for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
				hasA |= p.getUuid().equals(a);
				hasB |= p.getUuid().equals(b);
			}
			helper.assertTrue(hasA && hasB, "not both in the PC (mother " + hasA + ", father " + hasB + ")");
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.succeed();
		});
	}

	/**
	 * An egg makes a jobless villager by a pasture a Daycare Keeper (never by herself); with config daycareKeepers off, no
	 * job and no eggs; her pairs survive a save and reload.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void anEggMakesAKeeperAndTheSwitchTurnsHerOff(GameTestHelper helper) {
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		helper.assertTrue(Stations.at(BuiltInRegistries.BLOCK.get(c("pasture"))).map(s -> !s.byItself() && s.has(ModVillagers.DAYCARE_KEEPER))
			.orElse(false), "the pasture isn't a Daycare Keeper's station given by an item");
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		try {
			DaycareKeepers.ENABLED = false;
			Stations.choose(player, villager, new ItemStack(Items.EGG));
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"switched off, an egg still gave a job: " + villager.getVillagerData().getProfession());
		} finally {
			DaycareKeepers.ENABLED = true;
		}
		Stations.choose(player, villager, new ItemStack(Items.EGG));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.DAYCARE_KEEPER, "an egg made a " + villager.getVillagerData().getProfession());
		// Her pair, saved and loaded again
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer owner = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(owner, villager, mother, father), "the pair wasn't left");
		CompoundTag saved = new CompoundTag();
		villager.save(saved);
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(saved);
		helper.assertTrue(DaycareKeepers.pairs(loaded).size() == 1 && DaycareKeepers.pairs(loaded).get(0).owner().equals(owner.getUUID()),
			"the pair didn't survive a reload: " + DaycareKeepers.pairs(loaded));
		// Switched off: the dawns pass, no eggs
		try {
			DaycareKeepers.ENABLED = false;
			daysPass(villager, 3);
			DaycareKeepers.dawn(helper.getLevel(), villager, RandomSource.create(0));
			helper.assertTrue(eggs(villager) == 0, "switched off, she still found eggs");
		} finally {
			DaycareKeepers.ENABLED = true;
		}
		helper.succeed();
	}
}
