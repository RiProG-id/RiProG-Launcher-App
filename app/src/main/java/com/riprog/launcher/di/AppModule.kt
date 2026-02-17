package com.riprog.launcher.di

import androidx.room.Room
import com.riprog.launcher.data.local.db.LauncherDatabase
import com.riprog.launcher.data.local.datastore.SettingsDataStore
import com.riprog.launcher.data.repository.LauncherRepository
import com.riprog.launcher.ui.viewmodel.LauncherViewModel
import com.riprog.launcher.utils.SettingsManager
import com.riprog.launcher.manager.FolderManager
import com.riprog.launcher.manager.GridManager
import com.riprog.launcher.manager.WidgetManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
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
    single { SettingsManager(androidContext(), get()) }
    single { LauncherRepository(androidContext(), get(), get()) }

    factory { GridManager() }

    viewModel { LauncherViewModel(get()) }
}
