package com.jing.ddys

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.jing.ddys.detail.DetailViewModel
import com.jing.ddys.history.PlayHistoryViewModel
import com.jing.ddys.main.MainViewModel
import com.jing.ddys.playback.PlaybackViewModel
import com.jing.ddys.repository.HomeRepository
import com.jing.ddys.repository.HttpUtil
import com.jing.ddys.room.Dy555Database
import com.jing.ddys.search.SearchResultViewModel
import com.jing.ddys.search.SearchViewModel
import com.jing.ddys.setting.SettingsViewModel
import com.jing.ddys.update.ApkDownloader
import com.jing.ddys.update.ApkInstallLauncher
import com.jing.ddys.update.UpdateManager
import com.jing.ddys.update.UpdateRepository
import com.jing.ddys.update.UpdateViewModel
import com.jing.ddys.watchtogether.WatchTogetherClient
import com.jing.ddys.watchtogether.WatchTogetherViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.Executors

class DdysApplication : Application(), ImageLoaderFactory {

    private val TAG = DdysApplication::class.java.simpleName

    override fun onCreate() {
        super.onCreate()
        context = this
        startKoin {
            androidLogger()
            androidContext(this@DdysApplication)
            modules(viewModelModule(), roomModule())
        }
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        lateinit var context: DdysApplication
            private set

        val imageLoader by lazy {
            ImageLoader.Builder(context).okHttpClient(HttpUtil.okHttpClient).build()
        }
    }

    private fun viewModelModule() = module {
        viewModelOf(::MainViewModel)
        viewModelOf(::SearchViewModel)
        viewModel { parametersHolder -> DetailViewModel(parametersHolder.get(), get(), get()) }
        viewModel { parametersHolder -> SearchResultViewModel(parametersHolder.get()) }
        viewModel { parametersHolder ->
            PlaybackViewModel(
                parametersHolder.get(), parametersHolder.get(), get(), get()
            )
        }
        viewModelOf(::PlayHistoryViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::UpdateViewModel)
        viewModelOf(::WatchTogetherViewModel)

    }

    private fun roomModule() = module {
        single {
            Room.databaseBuilder(this@DdysApplication, Dy555Database::class.java, "ddys").apply {
                addMigrations(Dy555Database.MIGRATION_1_2)
                if (BuildConfig.DEBUG) {
                    val queryCallback = object : RoomDatabase.QueryCallback {
                        override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                            Log.i(TAG, "room sql: $sqlQuery  args: $bindArgs")
                        }
                    }
                    setQueryCallback(queryCallback, Executors.newSingleThreadExecutor())
                }
            }.build()
        }

        single {
            get<Dy555Database>().searchHistoryDao()
        }

        single {
            get<Dy555Database>().videoHistoryDao()
        }

        single {
            get<Dy555Database>().episodeHistoryDao()
        }

        single {
            get<Dy555Database>().homeVideoCacheDao()
        }

        single { HomeRepository(get()) }
        single { UpdateRepository() }
        single { ApkDownloader(this@DdysApplication) }
        single { ApkInstallLauncher() }
        single { UpdateManager(this@DdysApplication, get(), get(), get()) }
        single { WatchTogetherClient() }

    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}
