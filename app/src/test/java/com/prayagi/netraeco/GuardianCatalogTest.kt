package com.prayagi.netraeco

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class GuardianCatalogTest {
    private fun catalog(id:String=GUARDIAN_ID, repo:String=GUARDIAN_REPO, pkg:String=GUARDIAN_PACKAGE):String =
        JSONObject().put("projects",JSONArray().put(JSONObject().put("id",id).put("name","Netra Battery Guardian").put("repo",repo).put("package",pkg))).toString()
    @Test fun exactGuardianIdentityIsVisibleAndInstallerBound() {
        val app=Net.parseCatalog(catalog()).single()
        assertEquals(GUARDIAN_ID,app.id)
        assertTrue(isTrustedRepo(GUARDIAN_REPO))
        assertTrue(InstalledApps.knownPackages.contains(GUARDIAN_PACKAGE))
        assertEquals(GUARDIAN_ID,EcoAppRoute.appId("https://prayagi-store-and-services.github.io/netra-eco/app/guardian/"))
        val c=RuntimeEnvironment.getApplication()
        InstallerGate.remember(c,app,LatestRelease("v1.0.0","1.0.0",1,"","","f115e902aa9e191c4367519f274c26bf6adaf0692f12c57493af151f1e6f467f",13300013))
        val signer="543b46402206ae0022d352f65bea618386bd7aa681b4d5bcfb5ce02bac49c8c4"
        assertEquals(signer,InstallerGate.expectedSigner(GUARDIAN_PACKAGE))
        assertTrue(InstallerGate.identityMatches(GUARDIAN_PACKAGE,"1.0.0",1,GUARDIAN_PACKAGE,"1.0.0",1,setOf(signer)))
        assertFalse(InstallerGate.identityMatches(GUARDIAN_PACKAGE,"1.0.0",1,GUARDIAN_PACKAGE,"1.0.0",1,setOf("b".repeat(64))))
    }
    @Test fun exceptionDoesNotTrustOtherOwnerReposOrRelabeledGuardian() {
        assertFalse(isTrustedRepo("prayagideepak-collab/another-app"))
        assertFalse(isTrustedRepo(GUARDIAN_REPO+"/../evil"))
        assertTrue(Net.parseCatalog(catalog(repo="prayagideepak-collab/another-app")).isEmpty())
        assertTrue(Net.parseCatalog(catalog(pkg="wrong.pkg")).isEmpty())
        assertTrue(Net.parseCatalog(catalog(id="kbc")).isEmpty())
        assertTrue(Net.parseCatalog(catalog(repo="$ORG/KBC")).isEmpty())
        assertTrue(Net.parseCatalog(catalog(id="kbc",repo="$ORG/KBC",pkg="com.aistudio.reasoningquiz.tark")).isNotEmpty())
    }
    @Test fun publishedGuardianReleaseResolvesHashFromApiWithoutLatestJson() {
        val app=Net.parseCatalog(catalog()).single()
        val body=JSONObject().put("tag_name","v1.0.0").put("draft",false).put("prerelease",false).put("published_at","2026-11-18T14:10:14Z")
            .put("assets",JSONArray().put(JSONObject().put("name","app-release.apk").put("size",13300013).put("digest","sha256:f115e902aa9e191c4367519f274c26bf6adaf0692f12c57493af151f1e6f467f"))).toString()
        val latest=Net.fetchLatest(app,textLoader={url,_ -> if(url=="https://api.github.com/repos/$GUARDIAN_REPO/releases/latest") body else null})!!
        assertEquals("1.0.0",latest.versionName)
        assertEquals(13300013L,latest.size)
        assertEquals("https://github.com/$GUARDIAN_REPO/releases/download/v1.0.0/app-release.apk",latest.apkUrl)
        assertEquals(Status.NotInstalled,statusForRelease(null,null,latest))
    }
}
