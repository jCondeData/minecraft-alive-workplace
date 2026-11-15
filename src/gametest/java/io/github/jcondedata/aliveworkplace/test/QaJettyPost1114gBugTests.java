package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

/**
 * QA (lane-b-1114-2137): the Fisher's Hut II's jetty posts must go down to the bed (ROADMAP 27.14). CountryWorkplaces'
 * build test of it failed on CI build 4139 (e70c45f2c) and 2 of 12 local copies: the site is done, a post's lowest block
 * (the bed is two below the deck) is still water. Twelve copies of the same build, each in a batch of its own; any one
 * failing is the bug.
 */
public class QaJettyPost1114gBugTests {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final int COPIES = 12;

	@GameTestGenerator
	public Collection<TestFunction> jettyPostsReachTheBed() {
		List<TestFunction> out = new ArrayList<>();
		for (CountryWorkplacesGameTests.Workplace w : CountryWorkplacesGameTests.workplaces()) {
			if (!w.entry().id().getPath().equals("fishers_hut_2")) {
				continue;
			}
			for (int i = 0; i < COPIES; i++) {
				String name = "qaJettyPost1114g_fishers_hut_2_" + i;
				out.add(new TestFunction(name, name, HUGE_AREA, 14000, 0, true, helper -> CountryWorkplacesGameTests.build(helper, w)));
			}
		}
		return out;
	}
}
