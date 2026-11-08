package io.github.magicalwig34653.visualinspector.product

import com.intellij.openapi.application.ApplicationInfo

/**
 * Runtime product guard (ADR 0009): one build, active only in WebStorm ("WS") and PhpStorm ("PS").
 * Uses the public `ApplicationInfo.build.productCode`; `PlatformUtils` is internal and not used.
 */
object ProductGuard {
    private val supportedProductCodes = setOf("WS", "PS")

    fun isSupportedProductCode(productCode: String?): Boolean = productCode in supportedProductCodes

    fun isSupportedIde(): Boolean = isSupportedProductCode(ApplicationInfo.getInstance().build.productCode)

    fun currentProductName(): String = ApplicationInfo.getInstance().versionName
}
