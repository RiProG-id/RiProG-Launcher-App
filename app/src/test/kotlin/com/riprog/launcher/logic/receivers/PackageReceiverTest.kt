package com.riprog.launcher.logic.receivers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.riprog.launcher.data.repository.AppRepository
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PackageReceiverTest {

    @Test
    fun testOnReceiveInvalidatesIconAndCallsCallback() {
        val model: AppRepository = mock()
        var callbackCalled = false
        val onPackageChanged = { callbackCalled = true }
        val receiver = PackageReceiver(model, onPackageChanged)

        val context: Context = mock()
        val intent: Intent = mock()
        val uri: Uri = mock()

        whenever(intent.data).thenReturn(uri)
        whenever(uri.schemeSpecificPart).thenReturn("com.test.app")

        receiver.onReceive(context, intent)

        verify(model).invalidateIcon("com.test.app")
        assert(callbackCalled)
    }
}
