///////////////////////////////////////////////////////////////////////////////
//  GRADLE CONFIGURATION
///////////////////////////////////////////////////////////////////////////////
plugins {
    `java`
    id("com.diffplug.spotless") version "5.10.2"
    id("org.sonarqube") version "3.1"
    jacoco
}
buildscript {
    repositories {
        mavenLocal()
        maven(url = "https://repo.eclipse.org/service/local/repositories/maven_central/content")
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.keyple:keyple-gradle:0.2.+")
    }
}
apply(plugin = "org.eclipse.keyple")

///////////////////////////////////////////////////////////////////////////////
//  APP CONFIGURATION
///////////////////////////////////////////////////////////////////////////////
repositories {
    mavenLocal()
    maven(url = "https://repo.eclipse.org/service/local/repositories/maven_central/content")
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}
dependencies {
    implementation("org.eclipse.keyple:keyple-java-reader-api:1.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-card-api:1.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-commons-api:2.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-service:2.0.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-service-resource:2.0.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-plugin-pcsc:2.0.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-utils:2.0.0-SNAPSHOT") { isChanging = true }
    implementation("org.eclipse.keyple:keyple-java-card-calypso:2.0.0-SNAPSHOT") { isChanging = true }
    implementation ("org.slf4j:slf4j-simple:1.7.25")
    implementation ("org.slf4j:slf4j-ext:1.7.25")
}

val javaSourceLevel: String by project
val javaTargetLevel: String by project
java {
    sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
    targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
    println("Compiling Java $sourceCompatibility to Java $targetCompatibility.")
    withJavadocJar()
    withSourcesJar()
}

///////////////////////////////////////////////////////////////////////////////
//  TASKS CONFIGURATION
///////////////////////////////////////////////////////////////////////////////
tasks {
    spotless {
        java {
            target("src/**/*.java")
            licenseHeaderFile("gradle/license_header.txt")
            importOrder("java", "javax", "org", "com", "")
            removeUnusedImports()
            googleJavaFormat()
        }
    }
    test {
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy("jacocoTestReport")
    }
    jacocoTestReport {
        dependsOn("test")
        reports {
            xml.isEnabled = true
            csv.isEnabled = false
            html.isEnabled = true
        }
    }
    sonarqube {
        properties {
            property("sonar.projectKey", "eclipse_" + project.name)
            property("sonar.organization", "eclipse")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.login", System.getenv("SONAR_LOGIN"))
            System.getenv("BRANCH_NAME")?.let {
                if (!"main".equals(it)) {
                    property("sonar.branch.name", it)
                }
            }
        }
    }
}
