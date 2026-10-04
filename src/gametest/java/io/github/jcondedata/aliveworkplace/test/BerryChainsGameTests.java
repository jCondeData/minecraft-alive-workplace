package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import io.github.jcondedata.aliveworkplace.berry.BerryChains.Mutation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/** ROADMAP 28.9: the Berry Breeder's plan, on a hand-made copy of Cobblemon's berry mutations (no Cobblemon here). */
public class BerryChainsGameTests implements FabricGameTest {
	private static ResourceLocation b(String name) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", name + "_berry");
	}

	private static final List<Mutation> MUTATIONS = List.of(
		new Mutation(b("cheri"), b("oran"), b("lum")),
		new Mutation(b("chesto"), b("oran"), b("lum")),
		new Mutation(b("cheri"), b("persim"), b("figy")),
		new Mutation(b("figy"), b("lum"), b("sitrus")),
		new Mutation(b("leppa"), b("lum"), b("hopo")),
		new Mutation(b("oran"), b("razz"), b("leppa")));

	/** Lum from Oran and Cheri: one step; already had: nothing to do; Sitrus: Lum first, then Lum with Figy. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBreederPlansTheChain(GameTestHelper helper) {
		Set<ResourceLocation> have = Set.of(b("oran"), b("cheri"), b("figy"));
		helper.assertTrue(BerryChains.plan(have, MUTATIONS, b("lum")).equals(Optional.of(List.of(MUTATIONS.get(0)))),
			"Lum: " + BerryChains.plan(have, MUTATIONS, b("lum")));
		helper.assertTrue(BerryChains.plan(have, MUTATIONS, b("oran")).equals(Optional.of(List.of())), "Oran is had already");
		helper.assertTrue(BerryChains.plan(have, MUTATIONS, b("sitrus")).equals(Optional.of(List.of(MUTATIONS.get(0), MUTATIONS.get(3)))),
			"Sitrus: " + BerryChains.plan(have, MUTATIONS, b("sitrus")));
		helper.assertTrue(BerryChains.next(have, MUTATIONS, b("sitrus")).equals(Optional.of(MUTATIONS.get(0))), "the first step is Lum");
		helper.succeed();
	}

	/** Out of reach: a missing parent with no way to make it; Hopo needs Leppa, which needs Razz nobody has. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBerryOutOfReachHasNoPlan(GameTestHelper helper) {
		Set<ResourceLocation> have = Set.of(b("oran"), b("cheri"));
		helper.assertTrue(BerryChains.plan(have, MUTATIONS, b("hopo")).isEmpty(), "Hopo without Razz: " + BerryChains.plan(have, MUTATIONS, b("hopo")));
		helper.assertTrue(BerryChains.plan(Set.of(), MUTATIONS, b("lum")).isEmpty(), "nothing had, nothing made");
		Set<ResourceLocation> withRazz = Set.of(b("oran"), b("cheri"), b("razz"));
		List<Mutation> hopo = BerryChains.plan(withRazz, MUTATIONS, b("hopo")).orElseThrow();
		helper.assertTrue(hopo.size() == 3 && hopo.get(hopo.size() - 1).result().equals(b("hopo"))
			&& hopo.subList(0, 2).containsAll(List.of(MUTATIONS.get(0), MUTATIONS.get(5))), "Hopo with Razz: " + hopo);
		helper.assertTrue(BerryChains.madeFrom(MUTATIONS, b("lum")).size() == 2, "the book lists both pairs that make Lum");
		helper.succeed();
	}

	/** The hall keeps the village's found berries through a save and load; a hall saved before 28.9 has none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHallKeepsTheFoundBerries(GameTestHelper helper) {
		net.minecraft.core.BlockPos first = new net.minecraft.core.BlockPos(0, 1, 0);
		net.minecraft.core.BlockPos second = new net.minecraft.core.BlockPos(2, 1, 0);
		helper.setBlock(first, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		helper.setBlock(second, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall = helper.getBlockEntity(first);
		io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity other = helper.getBlockEntity(second);
		helper.assertTrue(hall.berriesFound().isEmpty(), "a new hall has found no berries");
		helper.assertTrue(hall.findBerry(b("lum")) && !hall.findBerry(b("lum")) && hall.findBerry(b("oran")), "found once each");
		var registries = helper.getLevel().registryAccess();
		net.minecraft.nbt.CompoundTag saved = hall.saveWithoutMetadata(registries);
		other.loadWithComponents(saved, registries);
		helper.assertTrue(other.berriesFound().equals(Set.of(b("lum"), b("oran"))), "after loading: " + other.berriesFound());
		saved.remove("berriesFound");
		other.loadWithComponents(saved, registries);
		helper.assertTrue(other.berriesFound().isEmpty(), "an old save: " + other.berriesFound());
		helper.succeed();
	}
}
