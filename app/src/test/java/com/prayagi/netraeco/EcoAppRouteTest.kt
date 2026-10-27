package com.prayagi.netraeco
import org.junit.Assert.*
import org.junit.Test
class EcoAppRouteTest {
    private val root = "https://prayagi-store-and-services.github.io/netra-eco/app/"
    @Test fun allowlistedAppRoutes() {
        mapOf("bspn" to "battery-sentinel", "kbc" to "kbc", "hub" to "netra-hub", "privacy" to "prayagi-privacy", "trikaal" to "netra-trikaal", "player" to "netra-player", "charge" to "netra-charge", "eco" to "netra-eco").forEach { (code,id) -> assertEquals(id, EcoAppRoute.appId(root+code+"/")); assertEquals(id,EcoAppRoute.appId(root+code)) }
    }
    @Test fun rejectsUntrustedAndExecutableRoutes() {
        listOf(null,"",root+"unknown/",root+"kbc/?download=1",root+"kbc/#install",root+"%6bbc/",root+"../kbc/",root+"kbc/extra",root.replace("https:","http:")+"kbc/",root.replace(".io", ".io.evil.invalid")+"kbc/",root.replace("https://", "https://user@")+"kbc/",root.replace(".io/", ".io:443/")+"kbc/").forEach { assertNull(it,EcoAppRoute.appId(it)) }
    }
 @org.junit.Test fun emptyQueryOrFragmentAreRejected(){
  org.junit.Assert.assertNull(EcoAppRoute.appId("https://prayagi-store-and-services.github.io/netra-eco/app/kbc/?"))
  org.junit.Assert.assertNull(EcoAppRoute.appId("https://prayagi-store-and-services.github.io/netra-eco/app/kbc/#"))
 }
}
