package at.bernhardberger.tvhplayer

import at.bernhardberger.tvhplayer.debug.TestCredentialProvisioner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugApp : App() {
    private val provisioningScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        provisioningScope.launch {
            TestCredentialProvisioner(this@DebugApp).consumeStagedPayload()
        }
    }
}
