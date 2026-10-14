package com.sportshub.app

import android.app.Application
import com.sportshub.app.data.FollowStore
import com.sportshub.app.data.Repository
import com.sportshub.app.notify.Background
import com.sportshub.app.notify.Notifier

class SportsApp : Application() {
    val repo: Repository by lazy { Repository(this, FollowStore(this)) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Background.schedulePeriodic(this)
    }
}
