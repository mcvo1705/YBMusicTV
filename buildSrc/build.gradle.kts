plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // AGP Instrumentation API (AsmClassVisitorFactory, ...) + ASM. Cần ở runtime
    // của buildSrc để classloader cha nạp được factory; nhờ parent-delegation,
    // các interface này dùng chung với AGP của project (cùng version 8.7.3).
    implementation("com.android.tools.build:gradle-api:8.7.3")
    implementation("org.ow2.asm:asm:9.7")
}
