package com.zankup.appicon

import android.content.ComponentName
import android.content.pm.PackageManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * The launcher icon, as Strava's custom app icons: each variant is an activity-alias of MainActivity
 * named `….AppIcon<Variant>` (plugins/withAppIcons.js adds them), and the one enabled is the icon the
 * launcher shows. Changing it enables that alias first and then disables the others, so the app always
 * has one way in; the app keeps running.
 */
class AppIconModule : Module() {
  private val prefix = ".AppIcon"

  private fun aliases(): List<String> {
    val context = appContext.reactContext ?: return emptyList()
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
    val info = context.packageManager.getPackageInfo(context.packageName, flags)
    return info.activities?.map { it.name }?.filter { it.contains(prefix) } ?: emptyList()
  }

  private fun variant(alias: String) = alias.substringAfterLast(prefix).replaceFirstChar { it.lowercase() }

  override fun definition() = ModuleDefinition {
    Name("AppIcon")

    Function("current") {
      val context = appContext.reactContext ?: return@Function null
      val pm = context.packageManager
      aliases().firstOrNull { alias ->
        val state = pm.getComponentEnabledSetting(ComponentName(context.packageName, alias))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
          (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && variant(alias) == "default")
      }?.let { variant(it) }
    }

    Function("set") { name: String ->
      val context = appContext.reactContext ?: return@Function false
      val pm = context.packageManager
      val all = aliases()
      val chosen = all.firstOrNull { variant(it) == name } ?: return@Function false
      pm.setComponentEnabledSetting(
        ComponentName(context.packageName, chosen),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
      )
      for (alias in all) {
        if (alias == chosen) continue
        pm.setComponentEnabledSetting(
          ComponentName(context.packageName, alias),
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
          PackageManager.DONT_KILL_APP,
        )
      }
      true
    }
  }
}
