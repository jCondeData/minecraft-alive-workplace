package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * For test classes that ask {@link Crafting} for a plan, a chain or what's short themselves and assert on the answer
 * (B1321). A level's crafters share {@link Crafting#TICK_TRIALS} recipes a tick, and every test of a batch starts in the
 * same tick of the same level: 50 of the default batch's at once, beside whatever earlier tests left planning there. A
 * test that plans as it starts found the tick's planning used up by its neighbours, and read "no plan" and "nothing
 * short" as the answer (QaMixedLogs1116bGameTests, all six at once, only in the full suite).
 *
 * <p>Each test of a class that implements this starts with the tick's planning fresh for it. A test that plans in a
 * later tick calls {@link #fresh} there. What the budget does when it does run out is B1321GameTests' and
 * RecipeIndexGameTests' to check, each in a batch of its own.
 */
public interface FreshPlanning extends FabricGameTest {
	@Override
	default void invokeTestMethod(GameTestHelper helper, Method method) {
		fresh(helper);
		FabricGameTest.super.invokeTestMethod(helper, method);
	}

	/** The tick's planning in the test's level, as if nothing had planned in it yet. */
	static void fresh(GameTestHelper helper) {
		Crafting.freshPlanning(helper.getLevel());
	}
}
