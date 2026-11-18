pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "zankup-android"

include(":app")
include(":core:designsystem")
include(":core:domain")
include(":core:network")
include(":core:session")
include(":core:data")
include(":core:charts")
include(":core:map")
include(":core:ui")
include(":core:health")
include(":feature:home")
include(":feature:feed")
include(":feature:activity")
include(":feature:progress")
include(":feature:stats")
include(":feature:routes")
include(":feature:health")
include(":feature:you")
include(":feature:coach")
include(":feature:settings")
include(":feature:tools")
include(":feature:signin")
