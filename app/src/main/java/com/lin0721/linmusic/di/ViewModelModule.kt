package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.feature.settings.ui.SettingsViewModel
import com.lin0721.linmusic.feature.create.ui.CreateViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.music.ui.StyleDetailViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistViewModel
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.library.textimport.PlaylistTextImportViewModel
import com.lin0721.linmusic.feature.listendata.ui.ListenDataViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.OnlineLocalAudioPickerViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalAlbumAssociationViewModel
import com.lin0721.linmusic.feature.localmusic.ui.BatchAlbumMatchViewModel
import com.lin0721.linmusic.feature.localmusic.ui.settings.LocalMusicSettingsViewModel
import com.lin0721.linmusic.feature.localmusic.ui.tageditor.LocalTagEditorViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.profile.ui.FollowListViewModel
import com.lin0721.linmusic.feature.message.ui.MessageViewModel
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel
import com.lin0721.linmusic.feature.recent.ui.RecentPlayViewModel
import com.lin0721.linmusic.feature.cloud.ui.CloudViewModel
import com.lin0721.linmusic.feature.downloads.ui.DownloadsViewModel
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistCatalogViewModel
import com.lin0721.linmusic.feature.artist.ui.catalog.ArtistAlbumCorrectionsViewModel
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import com.lin0721.linmusic.feature.personallibrary.LibraryPageLayoutViewModel

/**
 * Koin ViewModel 层依赖注入模块
 *
 * 使用 [viewModelOf] 委托自动解析 ViewModel 的构造参数。
 */
val viewModelModule = module {

    viewModelOf(::HomeViewModel)
    viewModelOf(::MusicViewModel)
    viewModelOf(::StyleDetailViewModel)
    viewModelOf(::PodcastHomeViewModel)
    viewModelOf(::PodcastSubscribedViewModel)
    viewModelOf(::PodcastCategoryViewModel)
    viewModelOf(::PodcastToplistViewModel)
    viewModelOf(::RadioDetailViewModel)
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::ArtistViewModel)
    viewModelOf(::ArtistCatalogViewModel)
    viewModelOf(::ArtistAlbumCorrectionsViewModel)
    viewModel {
        SearchViewModel(
            repository = get(),
            historyPreferences = get(),
            playerManager = get(),
            userPreferences = get(),
            resourceProvider = get(),
            songCollectDelegate = get(),
            loadLikedSongIdsUseCase = get(),
            songLikeRepository = get(),
            syncProfileAfterLoginUseCase = get(),
            sourceProviders = getAll<AudioSourceProvider>(),
            searchProviders = getAll<PlatformSearchProvider>(),
            settingsPreferences = getOrNull(),
            sourcePreferences = getOrNull()
        )
    }
    viewModelOf(::PlaylistCategoryViewModel)
    single { com.lin0721.linmusic.feature.personallibrary.LibraryPageLayoutRepository(androidContext()) }
    viewModelOf(::LibraryPageLayoutViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::PlaylistTextImportViewModel)
    viewModelOf(::RecentPlayViewModel)
    viewModelOf(::CloudViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::ListenDataViewModel)
    viewModelOf(::LocalMusicViewModel)
    viewModelOf(::OnlineLocalAudioPickerViewModel)
    viewModelOf(::LocalAlbumAssociationViewModel)
    viewModelOf(::BatchAlbumMatchViewModel)
    viewModelOf(::LocalMusicSettingsViewModel)
    viewModelOf(::LocalTagEditorViewModel)
    viewModelOf(::NewWorksViewModel)
    viewModelOf(::CreateViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)

    viewModelOf(::MessageViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::FollowListViewModel)

}
