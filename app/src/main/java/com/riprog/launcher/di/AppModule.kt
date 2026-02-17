package com.riprog.launcher.di

import androidx.room.Room
import com.riprog.launcher.data.local.db.LauncherDatabase
import com.riprog.launcher.data.local.datastore.SettingsDataStore
import com.riprog.launcher.data.repository.HomeRepository
import com.riprog.launcher.ui.home.HomeViewModel
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.home.manager.FolderManager
import com.riprog.launcher.ui.home.manager.GridManager
import com.riprog.launcher.ui.home.manager.WidgetManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            LauncherDatabase::class.java,
            "launcher_db"
        ).build()
    }
    single { get<LauncherDatabase>().homeItemDao() }
    single { SettingsDataStore(androidContext()) }
    single { LauncherPreferences(androidContext(), get()) }
    single { HomeRepository(androidContext(), get(), get()) }

    factory { GridManager() }

    viewModelOf(::HomeViewModel)
}
