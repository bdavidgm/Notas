package com.bdavidgm.notas

import android.app.Application
import com.bdavidgm.notas.data.NotasRepository
import com.bdavidgm.notas.data.local.AppDatabase

class NotasApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: NotasRepository by lazy {
        NotasRepository(
            dao = database.notasDao(),
            appContext = applicationContext,
        )
    }
}
