pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        //maven { url = uri("https://chaquo.com/maven") } // Add this line
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        //maven { url = uri("https://chaquo.com/maven") } // Add this line
        maven {
            url = uri("https://download2.dynamsoft.com/maven/aar")
        }
        maven {
            url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/")
        }
        flatDir {
            dirs("libs")
        }
    }
}

rootProject.name = "Vehicle Data Recorder"
include(":app")
 