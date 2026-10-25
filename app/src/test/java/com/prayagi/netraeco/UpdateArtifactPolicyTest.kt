package com.prayagi.netraeco
import org.junit.Assert.*
import org.junit.Test
class UpdateArtifactPolicyTest {
    private val pkg = "com.prayagi.netraplayer"
    private val pin = UpdateArtifactPolicy.expectedSigner(pkg)!!
    private fun ok(target:String=pkg, archivePackage:String?=pkg, installed:Long?=30, archive:Long=31,
        expected:Long=31, name:String?="1.1.16.9", signers:Set<String> = setOf(pin), current:Set<String>?=setOf(pin)) =
        UpdateArtifactPolicy.identityMatches(target,archivePackage,installed,archive,expected,"1.1.16.9",name,current,signers)
    @Test fun validSelfUpdate() { assertTrue(ok()) }
    @Test fun otherPackageRejected() { assertFalse(ok(archivePackage="other")); assertFalse(ok(archivePackage=null)) }
    @Test fun downgradeAndSameRejected() { assertFalse(ok(archive=30)); assertFalse(ok(archive=29)); assertFalse(ok(expected=-1)) }
    @Test fun wrongCodeOrNameRejected() { assertFalse(ok(expected=32)); assertFalse(ok(name="other")); assertFalse(ok(name=null)) }
    @Test fun changedMissingAndExtraSignersRejected() { assertFalse(ok(signers=setOf("bad"))); assertFalse(ok(signers=emptySet())); assertFalse(ok(current=emptySet())); assertFalse(ok(signers=setOf(pin,"extra"))) }
    @Test fun firstInstallStillNeedsPinnedSigner() { assertTrue(ok(installed=null,current=null)); assertFalse(ok(installed=null,current=null,signers=setOf("bad"))) }
    @Test fun apiOnlyCodeUsesExactNameAndNewerArchive() { assertTrue(ok(expected=0)); assertFalse(ok(expected=0,archive=30)); assertFalse(ok(expected=0,name="wrong")) }
    @Test fun hubHoldAndUnknownPackagesRefused() { assertFalse(ok(target="com.aistudio.netrasensorhub.kxmpzq",archivePackage="com.aistudio.netrasensorhub.kxmpzq")); assertFalse(ok(target="unknown")) }
    @Test fun distinctChargeAndTrikaalPins() { assertNotEquals(pin,UpdateArtifactPolicy.expectedSigner("com.netracharge.app")); assertNotEquals(pin,UpdateArtifactPolicy.expectedSigner("com.prayagi.netratrikaal")); assertNotEquals(UpdateArtifactPolicy.expectedSigner("com.netracharge.app"),UpdateArtifactPolicy.expectedSigner("com.prayagi.netratrikaal")) }
    @Test fun catalogCannotSwapAppIdentityOrRepository() {
        assertTrue(UpdateArtifactPolicy.catalogMatches("netra-player",pkg,"prayagi-store-and-services/netra-player"))
        assertFalse(UpdateArtifactPolicy.catalogMatches("netra-player","com.prayagi.netraeco","prayagi-store-and-services/netra-player"))
        assertFalse(UpdateArtifactPolicy.catalogMatches("netra-player",pkg,"prayagi-store-and-services/netra-eco-app"))
        assertFalse(UpdateArtifactPolicy.catalogMatches("netra-hub","com.aistudio.netrasensorhub.kxmpzq","prayagi-store-and-services/netra-hub"))
    }
}
