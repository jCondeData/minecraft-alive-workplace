package io.github.jcondedata.aliveworkplace.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

/**
 * The nightly flake check (ROADMAP 22.7): repeats the GameTests named in the environment variable
 * {@code ALIVEWORKPLACE_REPEAT} ("Class#method,Class#method", classes in this package) {@code ALIVEWORKPLACE_REPEAT_TIMES}
 * times (default 10), every copy in a batch of its own so neighbours can't disturb it. Without the variable it adds no
 * tests, so the normal suite is unchanged. {@code tools/modtest/newtests.py} lists the tests added in the last day and
 * the nightly workflow runs only this class with them (it rewrites the test mod's entrypoints in its own checkout).
 * Copies are named {@code repeat_<n>_<class>_<method>}.
 */
public class RepeatNewTests {
	public static final String ENV = "ALIVEWORKPLACE_REPEAT";
	/** Tests longer than this many ticks are repeated 3 times, not 10. */
	static final int LONG = 12_000;

	@GameTestGenerator
	public Collection<TestFunction> repeats() {
		String wanted = System.getenv(ENV);
		List<TestFunction> out = new ArrayList<>();
		if (wanted == null || wanted.isBlank()) {
			return out;
		}
		int times = 10;
		try {
			times = Math.max(1, Integer.parseInt(System.getenv().getOrDefault(ENV + "_TIMES", "10").trim()));
		} catch (NumberFormatException ignored) {
			// the default
		}
		for (String spec : wanted.split("[,\\s]+")) {
			if (spec.isBlank()) {
				continue;
			}
			String[] parts = spec.split("#");
			if (parts.length != 2) {
				throw new IllegalArgumentException(ENV + ": '" + spec + "' isn't Class#method");
			}
			Method method = find(parts[0], parts[1]);
			GameTest test = method.getAnnotation(GameTest.class);
			String template = test.template().isEmpty() ? net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE : test.template();
			// Long builds (over 10 minutes of game time) get 3 copies: ten would keep the night's run going for hours.
			int copies = test.timeoutTicks() > LONG ? Math.min(times, 3) : times;
			for (int i = 0; i < copies; i++) {
				String name = ("repeat_" + i + "_" + parts[0] + "_" + parts[1]).toLowerCase();
				out.add(new TestFunction(name, name, template, test.timeoutTicks(), test.setupTicks(), true, helper -> call(method, helper)));
			}
		}
		return out;
	}

	private static Method find(String className, String methodName) {
		try {
			Class<?> type = Class.forName(RepeatNewTests.class.getPackageName() + "." + className);
			for (Method m : type.getDeclaredMethods()) {
				if (m.getName().equals(methodName) && m.isAnnotationPresent(GameTest.class) && m.getParameterCount() == 1) {
					m.setAccessible(true);
					return m;
				}
			}
			throw new IllegalArgumentException(ENV + ": no @GameTest " + className + "#" + methodName);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(ENV + ": no test class " + className, e);
		}
	}

	private static void call(Method method, GameTestHelper helper) {
		try {
			Object target = java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null
				: method.getDeclaringClass().getDeclaredConstructor().newInstance();
			method.invoke(target, helper);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException r) {
				throw r;
			}
			throw new RuntimeException(e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
