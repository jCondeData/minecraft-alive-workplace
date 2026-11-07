package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.work.Words;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * B862 (kin of B406/B824/B833/B842): the operators' {@code /workplace classes} lines at a count of one. A reseed that
 * cleared one villager said "1 villagers lost their class", and a one-villager village's status read "1 of 1 villagers
 * have a class". The command and the status build their lines with these helpers, so they're checked here.
 */
public class QaClassesOne1103GameTests implements FabricGameTest {
	private static void reads(GameTestHelper helper, Component line, String expected) {
		helper.assertValueEqual(line.getString(), expected, "the line");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaClassLinesReadInTheSingularAtOne(GameTestHelper helper) {
		Component name = Component.literal("Thornholm");
		reads(helper, ClassMigration.reseeded(name, 1),
			"Thornholm will find its places again: 1 villager lost their class and is given one over the next rounds.");
		reads(helper, ClassMigration.reseeded(name, 22),
			"Thornholm will find its places again: 22 villagers lost their class and are given one over the next rounds.");
		reads(helper, ClassMigration.reseeded(name, 0),
			"Thornholm will find its places again: 0 villagers lost their class and are given one over the next rounds.");
		reads(helper, ClassMigration.seeding(name, 1, 1), "Thornholm is finding its places: 1 of 1 villager has a class so far.");
		reads(helper, ClassMigration.seeding(name, 0, 1), "Thornholm is finding its places: 0 of 1 villager has a class so far.");
		reads(helper, ClassMigration.seeding(name, 1, 5), "Thornholm is finding its places: 1 of 5 villagers has a class so far.");
		reads(helper, ClassMigration.seeding(name, 16, 36), "Thornholm is finding its places: 16 of 36 villagers have a class so far.");
		reads(helper, ClassMigration.seeding(name, 0, 20), "Thornholm is finding its places: 0 of 20 villagers have a class so far.");
		reads(helper, Words.counted("command.aliveworkplace.classes.newcomers", 1, 1), "1 newcomer will get a class at the hall's next round.");
		reads(helper, Words.counted("command.aliveworkplace.classes.newcomers", 2, 2), "2 newcomers will get a class at the hall's next round.");
		helper.succeed();
	}
}
