plugins {
    `java-library`
}

group = "cysworld"
version = "0.1.0"

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Java 8 bytecode and API, so any Android screen reader can compile or link the core unchanged.
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options"))
}

tasks.test {
    useJUnit()
}
