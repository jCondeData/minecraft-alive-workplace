package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/** ROADMAP 28.9 with the real Cobblemon: the Berry Breeder reads Cobblemon's berry data and plans from it. */
public class BerryBreederCompatTests implements FabricGameTest {
	private static ResourceLocation b(String name) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", name + "_berry");
	}

	/** Every berry and its mutations come from Cobblemon's data; Sitrus from Oran, Cheri and Figy is Lum, then Sitrus. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBerryBookComesFromCobblemonsData(GameTestHelper helper) {
		BerryChains.BerryData data = BerryChains.BerryData.EXTENSION.call(d -> d, null);
		helper.assertTrue(data != null, "no berry data");
		helper.assertTrue(data.berries().size() >= 60 && data.berries().contains(b("lum")), "berries: " + data.berries().size());
		List<BerryChains.Mutation> mutations = data.mutations();
		helper.assertTrue(mutations.contains(new BerryChains.Mutation(b("cheri"), b("oran"), b("lum"))), "Oran + Cheri = Lum is missing");
		List<BerryChains.Mutation> plan = BerryChains.plan(Set.of(b("oran"), b("cheri"), b("figy")), mutations, b("sitrus")).orElseThrow();
		helper.assertTrue(plan.equals(List.of(new BerryChains.Mutation(b("cheri"), b("oran"), b("lum")),
			new BerryChains.Mutation(b("figy"), b("lum"), b("sitrus")))), "the chain to Sitrus: " + plan);
		helper.succeed();
	}
}
