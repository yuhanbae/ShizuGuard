package com.yuhanbae.shizuguard

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import rikka.shizuku.Shizuku

class PanicFreezeTileService : TileService() {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.yuhanbae.shizuguard", GuardUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("tile_guard_service")
        .debuggable(false)
        .version(1)

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val targets = PanicStore.getPanicPackages(this).toList()
        if (targets.isEmpty()) {
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                subtitle = "No apps tagged"
                updateTile()
            }
            return
        }

        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            qsTile?.apply {
                state = Tile.STATE_UNAVAILABLE
                subtitle = "Shizuku offline"
                updateTile()
            }
            return
        }

        val currentlyFrozen = PanicStore.isPanicActive(this)
        val nextFrozenState = !currentlyFrozen

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = IGuardService.Stub.asInterface(binder)
                Thread {
                    try {
                        service.batchSetEnabled(targets, !nextFrozenState)
                        PanicStore.setPanicActive(this@PanicFreezeTileService, nextFrozenState)
                    } finally {
                        try {
                            Shizuku.unbindUserService(userServiceArgs, this, true)
                        } catch (_: Exception) {}
                        syncTileState()
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        Shizuku.bindUserService(userServiceArgs, connection)
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val count = PanicStore.getPanicPackages(this).size
        val frozen = PanicStore.isPanicActive(this)

        tile.state = when {
            count == 0 -> Tile.STATE_INACTIVE
            frozen -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = if (count == 0) {
            "Tag apps in app"
        } else if (frozen) {
            "Frozen ($count)"
        } else {
            "Ready ($count)"
        }
        tile.updateTile()
    }
}
