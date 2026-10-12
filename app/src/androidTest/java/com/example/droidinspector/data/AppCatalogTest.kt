package com.example.droidinspector.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCatalogTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = AppCatalog(context.packageManager)

    @Test
    fun resolves_label_and_icon_of_an_installed_app() {
        val label = catalog.labels(listOf(context.packageName)).getValue(context.packageName)

        assertTrue("debería tener un nombre visible", label.label.isNotEmpty())
        assertTrue("el nombre no debería ser el paquete", label.label != context.packageName)
        assertNotNull("debería tener icono", label.icon)
    }

    @Test
    fun falls_back_to_the_package_name_when_the_app_is_unknown() {
        val unknown = "com.example.does.not.exist"

        val label = catalog.labels(listOf(unknown)).getValue(unknown)

        assertEquals(unknown, label.label)
        assertEquals(null, label.icon)
    }

    @Test
    fun resolves_every_requested_package_once() {
        val packages = listOf(context.packageName, context.packageName, "android")

        val labels = catalog.labels(packages)

        assertEquals(2, labels.size)
    }
}
