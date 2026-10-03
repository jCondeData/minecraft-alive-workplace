package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A build site in a save opens again as it was, and a damaged one (a key missing) is skipped instead of crashing the
 * world's load. Found by the full check's mutants (ROADMAP 21.2): loosening {@code BuildSite.load}'s checks went
 * unnoticed.
 */
public class BuildSiteSaveGameTests implements FabricGameTest {
	private static BuildSite site() {
		return new BuildSite(UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"), UUID.fromString("12345678-9abc-def0-1234-56789abcdef0"),
			"Jesse", ResourceLocation.parse("aliveworkplace:cottage"),
			new BlueprintData.Placement(ResourceLocation.parse("minecraft:overworld"), new BlockPos(120, 64, -45), Rotation.CLOCKWISE_90, Mirror.NONE));
	}

	/** A saved site loads back with its id, owner, blueprint and placement. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSavedBuildSiteLoadsAsItWas(GameTestHelper helper) {
		BuildSite site = site();
		BuildSite back = BuildSite.load(site.save());
		helper.assertTrue(back != null, "a saved site didn't load");
		helper.assertTrue(back.id().equals(site.id()) && back.owner().equals(site.owner()), "id or owner changed: " + back.id() + " " + back.owner());
		helper.assertTrue(back.structure().equals(site.structure()), "blueprint changed: " + back.structure());
		helper.assertTrue(back.placement().equals(site.placement()), "placement changed: " + back.placement());
		helper.succeed();
	}

	/**
	 * A site missing its placement, id or owner (a damaged save) is skipped: no crash, no half site. One missing its
	 * blueprint id doesn't crash the load either (it loads naming no blueprint, like a site whose blueprint file was
	 * deleted).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aDamagedBuildSiteIsSkipped(GameTestHelper helper) {
		CompoundTag saved = site().save();
		for (String key : List.of("structure", "placement", "id", "owner")) {
			helper.assertTrue(saved.contains(key), "the save has no '" + key + "' key: " + saved.getAllKeys());
			CompoundTag damaged = saved.copy();
			damaged.remove(key);
			BuildSite loaded;
			try {
				loaded = BuildSite.load(damaged);
			} catch (RuntimeException e) {
				throw new AssertionError("a site without '" + key + "' crashed the load: " + e, e);
			}
			helper.assertTrue(key.equals("structure") || loaded == null, "a site without '" + key + "' loaded anyway");
		}
		helper.succeed();
	}
}
