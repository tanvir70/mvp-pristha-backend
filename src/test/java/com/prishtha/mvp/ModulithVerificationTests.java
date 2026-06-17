package com.prishtha.mvp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithVerificationTests {

	@Test
	void verifyModulithStructure() {
		ApplicationModules modules = ApplicationModules.of(MvpApplication.class);
		modules.verify();
	}

	@Test
	void writeDocumentation() {
		ApplicationModules modules = ApplicationModules.of(MvpApplication.class);
		new Documenter(modules).writeDocumentation();
	}

}
