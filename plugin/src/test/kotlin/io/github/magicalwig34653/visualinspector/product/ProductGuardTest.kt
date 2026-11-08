package io.github.magicalwig34653.visualinspector.product

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductGuardTest {
    @Test fun webStormAndPhpStormAreSupported() {
        assertTrue(ProductGuard.isSupportedProductCode("WS"))
        assertTrue(ProductGuard.isSupportedProductCode("PS"))
    }

    @Test fun otherProductsAndNullAreRejected() {
        for (code in listOf("IU", "IC", "PY", "GO", "CL", "RD", "RR", "AI", "ws", "", null)) {
            assertFalse("$code", ProductGuard.isSupportedProductCode(code))
        }
    }
}
