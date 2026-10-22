package com.android.glume.di

import com.android.glume.data.ImmichRepository
import com.android.glume.data.SettingsStore
import com.android.glume.data.mock.MockImmichRepository
import com.android.glume.ui.screens.Session
import com.android.glume.ui.screens.albums.AlbumsViewModel
import com.android.glume.ui.screens.collection.CollectionSource
import com.android.glume.ui.screens.collection.CollectionViewModel
import com.android.glume.ui.screens.connect.ConnectViewModel
import com.android.glume.ui.screens.home.HomeViewModel
import com.android.glume.ui.screens.people.PeopleViewModel
import com.android.glume.ui.screens.places.PlacesViewModel
import com.android.glume.ui.screens.profiles.ProfilesViewModel
import com.android.glume.ui.screens.search.SearchViewModel
import com.android.glume.ui.screens.settings.SettingsViewModel
import com.android.glume.ui.screens.timeline.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin wiring. Swap [MockImmichRepository] for the Ktor-backed repository once the real server is
 * reachable; nothing else changes.
 */
val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<ImmichRepository> { MockImmichRepository(androidContext()) }
    single { SettingsStore(androidContext(), get()) }
    single { Session(get(), get(), get()) }

    viewModel { ConnectViewModel(get(), get()) }
    viewModel { ProfilesViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { TimelineViewModel(get()) }
    viewModel { AlbumsViewModel(get()) }
    viewModel { PeopleViewModel(get()) }
    viewModel { PlacesViewModel(get()) }
    viewModel { (source: CollectionSource) -> CollectionViewModel(source, get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), androidContext()) }
}
