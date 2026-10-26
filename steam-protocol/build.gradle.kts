plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.protobuf")
}
kotlin { jvmToolchain(17) }
dependencies {
    api("com.google.protobuf:protobuf-javalite:4.29.1")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.2"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.29.1" }
    generateProtoTasks {
        all().configureEach {
            builtins { maybeCreate("java").apply { option("lite") } }
        }
    }
}
