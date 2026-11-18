package com.zankup.android.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SealedTest {
    @Test
    fun survivesBeingStored() {
        val sealed = Sealed(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7, 6), Protection.STRONG)
        val back = Sealed.decode(sealed.encode(), Protection.STRONG)!!
        assertArrayEquals(sealed.iv, back.iv)
        assertArrayEquals(sealed.ciphertext, back.ciphertext)
        assertEquals(Protection.STRONG, back.protection)
    }

    @Test
    fun refusesWhatIsNotOne() {
        assertNull(Sealed.decode("nothing", Protection.DEVICE))
        assertNull(Sealed.decode("not base64!.at all", Protection.DEVICE))
    }
}
