package com.upivoicealert

import com.upivoicealert.domain.model.NotificationSource
import com.upivoicealert.utils.PackageNames
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSourceTest {

    @Test
    fun `known upi app packages classify as UPI_APP`() {
        assertEquals(NotificationSource.UPI_APP, NotificationSource.forPackage(PackageNames.GPAY))
        assertEquals(NotificationSource.UPI_APP, NotificationSource.forPackage(PackageNames.PHONEPE))
        assertEquals(NotificationSource.UPI_APP, NotificationSource.forPackage(PackageNames.PAYTM))
        assertEquals(NotificationSource.UPI_APP, NotificationSource.forPackage(PackageNames.BHIM))
        assertEquals(NotificationSource.UPI_APP, NotificationSource.forPackage(PackageNames.KOTAK))
    }

    @Test
    fun `sms and messaging apps classify as SMS`() {
        assertEquals(
            NotificationSource.SMS,
            NotificationSource.forPackage("com.google.android.apps.messaging")
        )
        assertEquals(NotificationSource.SMS, NotificationSource.forPackage("com.android.mms"))
    }

    @Test
    fun `unknown packages classify as UNKNOWN`() {
        assertEquals(NotificationSource.UNKNOWN, NotificationSource.forPackage("com.example.somebank"))
        assertEquals(NotificationSource.UNKNOWN, NotificationSource.forPackage(""))
    }
}
