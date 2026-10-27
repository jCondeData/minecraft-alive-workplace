package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, Create and optional mods, 2026-10-27), from the wiki's Create page ("Machine work"): each machine is a data
 * file a datapack can add; "Files for a mod that isn't loaded, or naming a block, job or recipe type that doesn't exist,
 * are skipped quietly", and a file says "the most carried per trip (batch)" and "the speed that counts as turning
 * (min_speed)". Without Create every shipped file is skipped; a datapack's file of vanilla things is read, with the
 * defaults when it leaves them out; one naming anything that isn't there is skipped, whichever part it is.
 */
public class QaMachineFilesGameTests implements FabricGameTest {
	private static final String GOOD = "{\"machine\": \"minecraft:smoker\", \"job\": \"minecraft:butcher\", \"recipe_type\": \"minecraft:smoking\","
		+ " \"makes\": [{\"item\": \"minecraft:cooked_beef\"}]}";

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCreateTheShippedFilesAreSkipped(GameTestHelper helper) {
		helper.assertFalse(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "this test is for a pack without Create");
		helper.assertTrue(MachineWorkFiles.all().isEmpty(), "machine files were read without Create: " + MachineWorkFiles.all());
		helper.assertTrue(read(helper, "{\"requires\": \"create\"," + GOOD.substring(1)) == null, "a file that requires Create was read without it");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aDatapacksFileOfVanillaThingsIsReadWithItsDefaults(GameTestHelper helper) {
		MachineWorkFiles.Machine m = read(helper, GOOD);
		helper.assertTrue(m != null, "a file of vanilla things wasn't read");
		helper.assertTrue(m.machine() == Blocks.SMOKER && m.job().equals(ResourceLocation.withDefaultNamespace("butcher"))
			&& m.recipeType().equals(ResourceLocation.withDefaultNamespace("smoking")), "read as " + m);
		helper.assertTrue(m.batch() == 16, "the batch left out isn't 16: " + m.batch());
		helper.assertTrue(m.minSpeed() == 1f, "min_speed left out isn't 1 RPM: " + m.minSpeed());
		helper.assertTrue(m.makes().size() == 1 && m.makes().get(0).item() == Items.COOKED_BEEF && m.makes().get(0).keep() == 64,
			"what it makes (64 kept when left out): " + m.makes());
		MachineWorkFiles.Machine zero = read(helper, GOOD.substring(0, GOOD.length() - 1) + ", \"batch\": 0, \"min_speed\": 30}");
		helper.assertTrue(zero != null && zero.batch() == 1 && zero.minSpeed() == 30f, "a batch of 0 is carried as " + (zero == null ? "nothing" : zero.batch()));
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFileNamingAnythingThatIsntThereIsSkipped(GameTestHelper helper) {
		String[][] swaps = {
			{"minecraft:smoker", "nosuchmod:press"},
			{"minecraft:butcher", "nosuchmod:pressman"},
			{"minecraft:smoking", "nosuchmod:pressing"},
			{"minecraft:cooked_beef", "nosuchmod:sheet"},
		};
		for (String[] swap : swaps) {
			helper.assertTrue(read(helper, GOOD.replace(swap[0], swap[1])) == null, "a file naming " + swap[1] + " wasn't skipped");
		}
		helper.assertTrue(read(helper, "{\"requires\": \"nosuchmod\"," + GOOD.substring(1)) == null, "a file for a mod that isn't loaded wasn't skipped");
		helper.succeed();
	}

	private static MachineWorkFiles.Machine read(GameTestHelper helper, String json) {
		try {
			Method read = MachineWorkFiles.class.getDeclaredMethod("read", ResourceLocation.class, JsonObject.class);
			read.setAccessible(true);
			return (MachineWorkFiles.Machine) read.invoke(null, ResourceLocation.fromNamespaceAndPath("qa", "file"), JsonParser.parseString(json).getAsJsonObject());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("couldn't read the machine file: " + e.getCause(), e);
		}
	}
}
