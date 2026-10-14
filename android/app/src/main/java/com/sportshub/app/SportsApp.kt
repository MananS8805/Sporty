package com.sportshub.app

import android.app.Application
import com.sportshub.app.data.FollowStore
import com.sportshub.app.data.Repository

class SportsApp : Application() {
    val repo: Repository by lazy { Repository(this, FollowStore(this)) }
}
