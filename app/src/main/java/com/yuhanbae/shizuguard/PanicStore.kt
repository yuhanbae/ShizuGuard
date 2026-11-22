package com.yuhanbae.shizuguard

import android.content.Context

object PanicStore {
    private const val PREF_NAME = "shizuguard_prefs"
    private const val KEY_PANIC_LIST = "panic_packages"
    private const val KEY_PANIC_ACTIVE = "panic_active"

    fun getPanicPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PANIC_LIST, emptySet())?.toSet() ?: emptySet()
    }

    fun togglePanicPackage(context: Context, packageName: String): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getPanicPackages(context).toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_PANIC_LIST, current).apply()
        return current
    }

    fun isPanicActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PANIC_ACTIVE, false)
    }

    fun setPanicActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PANIC_ACTIVE, active).apply()
    }
}
