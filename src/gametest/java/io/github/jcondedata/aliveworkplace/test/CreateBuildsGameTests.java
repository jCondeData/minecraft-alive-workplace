package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

/**
 * ROADMAP 39.4 without Create: the Watermill, the Windmill and the Workshop aren't at the Blueprint Table (their blocks
 * don't exist), they still load by id (a blueprint already handed out keeps working), they belong to {@code createBuilds},
 * and their text reads. With Create: {@code CreateBuildsCompatTests}.
 */
public class CreateBuildsGameTests implements FabricGameTest {
	/** Without Create the three builds are hidden at the Table (and so in the creative tab), and a build of no mod isn't. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void hiddenWithoutCreate(GameTestHelper helper) {
		helper.assertTrue(!Platform.get().isModLoaded("create"), "this suite runs without Create");
		List<ResourceLocation> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		Language en = Language.getInstance();
		for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
			helper.assertTrue(!listed.contains(entry.id()), entry.id() + " is listed without Create");
			helper.assertTrue(!CreateBuilds.listed(entry.id()) && CreateBuilds.isCreateBuild(entry.id()), entry.id() + " isn't held back");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(entry.id()), entry.id() + " doesn't load by id");
			helper.assertTrue("createBuilds".equals(StarterBlueprints.expansionSwitch(entry.id())), entry.id() + " isn't behind createBuilds");
			String path = entry.id().getPath();
			helper.assertTrue(en.has("blueprint.aliveworkplace." + path) && en.has("blueprint_desc.aliveworkplace." + path), path + "'s text is missing");
		}
		helper.assertTrue(CreateBuilds.listed(StarterBlueprints.WELL.id()) && listed.contains(StarterBlueprints.WELL.id()),
			"a build of no mod is hidden");
		helper.assertTrue(en.has("aliveworkplace.config.createBuilds") && en.has("aliveworkplace.config.createBuilds.tooltip"),
			"the createBuilds switch has no text");
		helper.succeed();
	}

	/** The switch off hides them too (here they're hidden anyway); it's read back from the config on reload. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theSwitchHoldsThemBack(GameTestHelper helper) {
		boolean was = CreateBuilds.ENABLED;
		try {
			CreateBuilds.ENABLED = false;
			for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
				helper.assertTrue(!CreateBuilds.listed(entry.id()), entry.id() + " listed with createBuilds off");
			}
			helper.assertTrue(CreateBuilds.listed(StarterBlueprints.WELL.id()), "createBuilds off hides a build of no mod");
		} finally {
			CreateBuilds.ENABLED = was;
		}
		helper.succeed();
	}
}
