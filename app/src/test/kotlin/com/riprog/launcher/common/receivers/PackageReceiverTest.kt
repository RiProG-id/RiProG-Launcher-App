package com.riprog.launcher.common.receivers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.riprog.launcher.data.repository.AppRepository
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertTrue

class PackageReceiverTest {

    @Test
    fun testOnReceiveInvalidatesIconAndCallsCallback() {
        val model = mock<AppRepository>()
        var callbackCalled = false
        val onPackageChanged = { callbackCalled = true }
        val receiver = PackageReceiver(model, onPackageChanged)

        val context = mock<Context>()
        val intent = mock<Intent>()
        val uri = mock<Uri>()

        whenever(intent.data).thenReturn(uri)
        whenever(uri.schemeSpecificPart).thenReturn("com.test.app")

        receiver.onReceive(context, intent)

        verify(model).invalidateIcon("com.test.app")
        assertTrue(callbackCalled)
    }
}
