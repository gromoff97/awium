package io.github.gromoff97.awium.conditions.support

import java.nio.file.Path

internal val coreJar: Path = Path.of(requireNotNull(System.getProperty("awium.core.jar")))
internal val conditionsJar: Path = Path.of(requireNotNull(System.getProperty("awium.conditions.jar")))
