package com.example.gemini.data.harness

import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * On-device Android project scaffolding engine for JIN.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 */
object AndroidProjectTemplateGenerator {

    fun packageName(name: String): String {
        val raw = projectSlug(name).replace('-', '_').take(40).ifBlank { "app" }
        val suffix = if (raw.first().isLetter()) raw else "app_$raw"
        return "com.jin.$suffix"
    }

    fun projectSlug(name: String): String {
        val ascii = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .trimEnd('-')
        return ascii.ifBlank { "project" }
    }

    fun generate(destination: File, appName: String, template: AndroidTemplate) {
        val parent = requireNotNull(destination.parentFile)
        val staging = File(parent, ".${destination.name}.jin-template")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Could not prepare the Android project staging folder" }
        try {
            writeCommon(staging, appName, packageName(appName), template)
            when (template) {
                AndroidTemplate.COMPOSE -> writeCompose(staging, packageName(appName))
                AndroidTemplate.XML -> writeXml(staging, packageName(appName))
            }
            if (destination.exists()) {
                check(destination.listFiles().isNullOrEmpty()) { "Project folder is not empty" }
                check(destination.delete()) { "Could not clean existing empty folder" }
            }
            check(staging.renameTo(destination)) { "Could not finalize Android project creation" }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun writeCommon(root: File, appName: String, namespace: String, template: AndroidTemplate) {
        val compose = template == AndroidTemplate.COMPOSE
        write(root, "settings.gradle.kts", """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
            rootProject.name = ${kotlinString(appName)}
            include(":app")
        """.trimIndent())
        write(root, "build.gradle.kts", """
            plugins {
                id("com.android.application") version "8.11.0" apply false
                id("org.jetbrains.kotlin.android") version "1.9.22" apply false
            }
        """.trimIndent())
        write(root, "gradle.properties", """
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
        """.trimIndent())
        write(root, "app/build.gradle.kts", """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "$namespace"
                compileSdk = 36

                defaultConfig {
                    applicationId = "$namespace"
                    minSdk = 28
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
                buildFeatures { compose = $compose }
                ${if (compose) "composeOptions { kotlinCompilerExtensionVersion = \"1.5.10\" }" else ""}
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                ${if (compose) """
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.compose.ui:ui:1.6.0")
                implementation("androidx.compose.foundation:foundation:1.6.0")
                implementation("androidx.compose.material3:material3:1.2.0")
                """.trimIndent() else """
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("com.google.android.material:material:1.11.0")
                """.trimIndent()}
            }
        """.trimIndent())
        write(root, ".gitignore", """
            .gradle/
            local.properties
            **/build/
            *.iml
        """.trimIndent())
        write(root, ".jin/android-project.json", """{"schemaVersion":1,"template":"${template.name}","packageName":"$namespace"}""")
        write(root, "app/src/main/res/values/strings.xml", """
            <resources><string name="app_name">${xmlEscape(appName)}</string></resources>
        """.trimIndent())
    }

    private fun writeCompose(root: File, namespace: String) {
        val packagePath = namespace.replace('.', '/')
        write(root, "app/src/main/AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:theme="@style/Theme.JinGeneratedApp" android:label="@string/app_name">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent())
        write(root, "app/src/main/res/values/themes.xml", """
            <resources>
                <style name="Theme.JinGeneratedApp" parent="android:style/Theme.Material.Light.NoActionBar">
                    <item name="android:fontFamily">sans</item>
                    <item name="android:colorAccent">#2563EB</item>
                    <item name="android:statusBarColor">#F8FAFC</item>
                    <item name="android:navigationBarColor">#F8FAFC</item>
                    <item name="android:windowLightStatusBar">true</item>
                    <item name="android:windowLightNavigationBar">true</item>
                </style>
            </resources>
        """.trimIndent())
        write(root, "app/src/main/java/$packagePath/MainActivity.kt", """
            package $namespace

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            GreetingScreen()
                        }
                    }
                }
            }

            @Composable
            fun GreetingScreen() {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Built on Device with JIN", style = MaterialTheme.typography.headlineMedium)
                    Text(text = "Samir Ahmmed Mobile Intelligence", style = MaterialTheme.typography.bodyMedium)
                }
            }
        """.trimIndent())
    }

    private fun writeXml(root: File, namespace: String) {
        val packagePath = namespace.replace('.', '/')
        write(root, "app/src/main/AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:theme="@style/Theme.JinGeneratedApp" android:label="@string/app_name">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent())
        write(root, "app/src/main/res/values/themes.xml", """
            <resources>
                <style name="Theme.JinGeneratedApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
                    <item name="colorPrimary">#2563EB</item>
                    <item name="android:statusBarColor">#F8FAFC</item>
                </style>
            </resources>
        """.trimIndent())
        write(root, "app/src/main/res/layout/activity_main.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="16dp">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Built on Device with JIN"
                    android:textSize="22sp"
                    android:textStyle="bold" />
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Samir Ahmmed Mobile Intelligence"
                    android:textSize="14sp" />
            </LinearLayout>
        """.trimIndent())
        write(root, "app/src/main/java/$packagePath/MainActivity.kt", """
            package $namespace

            import android.os.Bundle
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                }
            }
        """.trimIndent())
    }

    private fun write(root: File, relative: String, content: String) {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        file.writeText(content.trimIndent())
    }

    private fun kotlinString(value: String): String = "\"${value.replace("\"", "\\\"")}\""
    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
