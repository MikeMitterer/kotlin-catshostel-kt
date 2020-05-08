package at.mikemitterer.catshostel.jwt

import at.mikemitterer.catshostel.auth.createJWT
import at.mikemitterer.catshostel.auth.getPrivateKey
import at.mikemitterer.catshostel.auth.getPublicKey
import at.mikemitterer.catshostel.auth.stripPEMMarker
import at.mikemitterer.catshostel.main
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.*
import org.joda.time.DateTime
import org.junit.Ignore
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.security.interfaces.RSAPublicKey

/**
 *
 *
 * @since   30.04.20, 09:54
 */
class JavaWebTokenTest {
    private val logger = LoggerFactory.getLogger(JavaWebTokenTest::class.java.simpleName)

    private val publicKeyFile = File("./resources/rsakeys/jwt.pub.pem")
    private val privateKeyFile = File("./resources/rsakeys/jwt.pkcs8.pem")

    @Test
    @Ignore("KeyCloak must be running")
    fun testKeyCloak() = runBlocking {
        val realm = "demo"
        val authHost = "http://localhost:9000" // KeyCloak-Server
        val issuer = "${authHost}/auth/realms/${realm}"

        val token = "${issuer}/protocol/openid-connect/token"
        val certs = "${issuer}/protocol/openid-connect/certs"
        val config = "${issuer}/.well-known/openid-configuration"

        val username = "mike"
        val password = "mike"
        val clientID = "vue-test-app"
        val grantType = "password"

        val client = HttpClient(Apache) {
            // install(Auth) {
            //     basic {
            //         username = "mike"
            //         password = "mike"
            //     }
            // }
        }

        val responseJWT = client.submitForm<String>(token, Parameters.build {
            append("client_id", clientID)
            append("grant_type", grantType)
            append("username", username)
            append("password", password)
        })

        val gson = GsonBuilder().setPrettyPrinting().create();
        val obj = gson.fromJson(responseJWT, JsonObject::class.java)
        val json = gson.toJson(obj)
        // logger.info(json)

        val accessToken = obj.get("access_token").asString
        val refreshToken = obj.get("refresh_token").asString
        // val accessToken = tokenToTestExpired
        val jwt = JWT.decode(accessToken)

        //val jwkProvider = UrlJwkProvider("https://YOUR_TENANT.auth0.com/")
        val jwkProvider = UrlJwkProvider( URL(certs)).get(jwt.keyId);
        val pubKey = jwkProvider.publicKey
        logger.info(pubKey.toString())

        val algorithm: Algorithm = Algorithm.RSA256(jwkProvider.publicKey as RSAPublicKey, null)

        val verifier: JWTVerifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .acceptExpiresAt(0)
                .build()

        val verified = verifier.verify(accessToken)
        logger.info("OK - Key verified!")

        val responseRefresh = client.submitForm<String>(token, Parameters.build {
            append("client_id", clientID)
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
        })
        val objRefresh = gson.fromJson(responseRefresh, JsonObject::class.java)
        val jsonRefresh = gson.toJson(objRefresh)
        
        logger.info(jsonRefresh)

        val accessTokenRefreshed = objRefresh.get("access_token").asString
        verifier.verify(accessTokenRefreshed)

        // Now with JWTS
        val jwtParser = Jwts.parserBuilder()
                .setSigningKey(jwkProvider.publicKey as RSAPublicKey)
                .build()

        val claims = jwtParser.parseClaimsJws(accessTokenRefreshed)
        logger.info("Header: ${claims.header}")
        logger.info("Body: ${gson.toJson(claims.body)}")
        logger.info("Signature: ${claims.signature}")
    }

    /**
     * Key-Generierung und Basis für Source:
     *      https://gist.github.com/destan/b708d11bd4f403506d6d5bb5fe6a82c5
     */
    @Test
    fun testCreateToken() {
        val privKey = getPrivateKey(privateKeyFile)
        val pubKey = getPublicKey(publicKeyFile)
        
        val algorithm = Algorithm.RSA256(pubKey, privKey)
        algorithm.shouldNotBeNull()

        val nowMillis = System.currentTimeMillis()
        val now = DateTime.now()

        // Reserve Claims:
        //      https://auth0.com/docs/tokens/concepts/jwt-claims#reserved-claims
        //      https://www.iana.org/assignments/jwt/jwt.xhtml#claims
        val token = JWT.create()

                // exp
                .withExpiresAt(now.plusMinutes(5).toDate()) // 300 secs

                // iat
                .withIssuedAt(now.toDate())

                // iss
                .withIssuer("mmit")

                // sub
                .withSubject("Subject?")

                .withClaim("typ", "Bearer")

                .sign(algorithm)

        token.shouldNotBeNullOrEmpty()

        logger.info(token)

        val algorithmForVerification: Algorithm = Algorithm.RSA256(pubKey, null)
        val verifier = JWT.require(algorithmForVerification)
                .withIssuer("mmit")
                .build() //Reusable verifier instance

        val jwt = verifier.verify(token)
        jwt.shouldNotBeNull()
    }

    @Test
    fun testCreateMitJJWT() {
        val privateKey = privateKeyFile.readText().stripPEMMarker()
        val now = DateTime.now()

        val jwt = createJWT(mutableMapOf(
                Claims.EXPIRATION to now.plusMinutes(5).toDate(),
                Claims.ISSUED_AT to now.toDate(),
                Claims.ISSUER to "mmit",
                Claims.AUDIENCE to "account",
                Claims.SUBJECT to "Subject",
                "typ" to "Bearer",
                "realm_access" to
                        mapOf("roles" to listOf<String>(
                                "offline_access",
                                "uma_authorization",
                                "vip"
                        ))
                ,
                "resource_access" to mapOf(
                        "vue-test-app" to
                                mapOf("roles" to listOf<String>(
                                        "device"
                                ))
                        ,
                        "account" to
                                mapOf("roles" to listOf<String>(
                                        "manage-account",
                                        "manage-account-links",
                                        "view-profile"
                                ))
                ),
                "scope" to "profile email",
                "email_verified" to true,
                "preferred_username" to "mike",
                "given_name" to "Mike",
                "family_name" to "Mitterer",
                "email" to "office@mikemitterer.at"
        ), privateKey)

        jwt.shouldNotBeNullOrEmpty()
        logger.info(jwt)

        val publicKey =  getPublicKey(publicKeyFile) {
            // Hier könnte man den public-Key zum testen ändern (verfälschen)
            // it.replace("a","b")
            it
        }

        val jwtParser = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()

        val claims = jwtParser.parseClaimsJws(jwt)
        claims.shouldNotBeNull()

        logger.info("Header: ${claims.header}")
        logger.info("Body: ${claims.body}")
        logger.info("Signature: ${claims.signature}")

        claims.body["email"].shouldBeEqualTo("office@mikemitterer.at")

        @Suppress("UNCHECKED_CAST")
        val realmAccess = claims.body["realm_access"] as Map<String, List<String>?>?
        realmAccess
                ?.get("roles")
                ?.get(2)
                .shouldBeEqualTo("vip")

        @Suppress("UNCHECKED_CAST")
        ((claims.body["resource_access"] as Map<String, Map<String, List<String>>>)
                ["account"]
                    ?.get("roles")
                    ?.get(2)
                    .shouldBeEqualTo("view-profile"))
    }

    /**
     * On the cmdline:
     *       curl -X POST -F 'username=test' -F 'password=password' http://0.0.0.0:8080/login
     */
    @Test
    fun testLogin() {
        withTestApplication({ main(testing = true) }) {
            val call = handleRequest(HttpMethod.Post, "/login") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf(
                        "username" to "test",
                        "password" to "password"
                ).formUrlEncode())
            }
            call.response.status().shouldBe(HttpStatusCode.OK)
            call.response.content?.contains(
                    """^[A-Za-z0-9-_=]+\.[A-Za-z0-9-_=]+\.?[A-Za-z0-9-_.+/=]*${'$'}""".toRegex())
                    ?.shouldBeTrue()
        }
    }

    /**
     * On the cmdline:
     *      TOKEN=$(curl -X POST -F 'username=test' -F 'password=password' http://0.0.0.0:8080/login)
     *      curl -H "Accept: application/json" -H "Authorization: Bearer ${TOKEN}" http://0.0.0.0:8080/protected/route/jwt
     */
    @Test
    fun testServerRequest() {
        val privateKey = privateKeyFile.readText().stripPEMMarker()
        val now = DateTime.now()

        val jwt = createJWT(mutableMapOf(
                Claims.EXPIRATION to now.plusMinutes(5).toDate(),
                Claims.ISSUED_AT to now.toDate(),
                Claims.ISSUER to "mmit",
                Claims.AUDIENCE to "account",
                Claims.SUBJECT to "Subject",
                "typ" to "Bearer",
                "realm_access" to
                        mapOf("roles" to listOf<String>(
                                "offline_access",
                                "uma_authorization",
                                "vip"
                        ))
                ,
                "resource_access" to mapOf(
                        "vue-test-app" to
                                mapOf("roles" to listOf<String>(
                                        "device"
                                ))
                        ,
                        "account" to
                                mapOf("roles" to listOf<String>(
                                        "manage-account",
                                        "manage-account-links",
                                        "view-profile"
                                ))
                ),
                "scope" to "profile email",
                "email_verified" to true,
                "preferred_username" to "mike",
                "given_name" to "Mike",
                "family_name" to "Mitterer",
                "email" to "office@mikemitterer.at"
        ), privateKey)

        withTestApplication({ main(testing = true) }) {
            val call = handleRequest(HttpMethod.Get, "/protected/route/jwt") {
                addHeader(HttpHeaders.Authorization, "Bearer $jwt")

            }
            call.response.status().shouldBe(HttpStatusCode.OK)
            call.response.content.shouldBeEqualTo("Hello 'mike'! (JWT)")
        }
    }

    private val tokenToTestExpired = """
        eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI3Z3U4eEVLbFVKeGFDWFRiZnVMaWxwM25MQVpmNURBQWdtTlplM3d0T284In0.
        eyJleHAiOjE1ODgyNTMwMDMsImlhdCI6MTU4ODI1MjcwMywianRpIjoiOWU0ZjUwODgtZDczOC00NGVhLTkyNTktZDc2ZWY2ZDFlOTM3IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwL2F1dGgvcmVhbG1zL2RlbW8iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiNWVmMWNmZDgtYTM0ZS00OGU3LWIzNTgtYTQ5ZTg2ZjM4ODNlIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidnVlLXRlc3QtYXBwIiwic2Vzc2lvbl9zdGF0ZSI6IjMyOTNmYmMwLTYwMmUtNGFhMS05YTAyLTFiZGU5Y2FhMjU1MyIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiaHR0cDovL2xvY2FsaG9zdDo4MDgwIiwiaHR0cDovL2xvY2FsaG9zdDo5MDkwIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIiwidmlwIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsidnVlLXRlc3QtYXBwIjp7InJvbGVzIjpbImRldmljZSJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJNaWtlIE1pdHRlcmVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoibWlrZSIsImdpdmVuX25hbWUiOiJNaWtlIiwiZmFtaWx5X25hbWUiOiJNaXR0ZXJlciIsImVtYWlsIjoib2ZmaWNlQG1pa2VtaXR0ZXJlci5hdCJ9.
        ekB1pt8eWL1Ez3gY_aCUqW56CapZUUxT1qwGKzKBhsDYsEBAruPdjz-fN_AuYWO-a4j7x6hCqsx4QytK6wrCGyS0tTrvkjcUM_m5BmZqAVLmLT7OFAQs0T0_w5scKxX8gEHyJfOAlhAhXRR4oulcEaIKGknqwTZm2FuOL_npmiGpo5Zvc2TFrL8zG_NRf9UnxYTV27Ss-7lJNH-tua34PkoH7zNyDXNeqzW4PKdRmgaLjGe7fED-j5Lm5udF7FLt5CkqCd64aqAHomLzTfcYcpWL7c9Wi_5DUEMjvRbvo8Hu62dUGZkMaLLKDaOmMoEYGZ2pc7LD6dFU1ISBrcJXjw        
    """.trimIndent().replace("\n","")

    private val tokenToTestExpired2 = """
        eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI3Z3U4eEVLbFVKeGFDWFRiZnVMaWxwM25MQVpmNURBQWdtTlplM3d0T284In0.
        eyJleHAiOjE1ODgyNTQ3NzAsImlhdCI6MTU4ODI1NDQ3MCwianRpIjoiZDc0NWZjMmUtOTAxMy00Nzc4LWIwYzAtZDlmZjA3ZmI3MTIyIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwL2F1dGgvcmVhbG1zL2RlbW8iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiNWVmMWNmZDgtYTM0ZS00OGU3LWIzNTgtYTQ5ZTg2ZjM4ODNlIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidnVlLXRlc3QtYXBwIiwic2Vzc2lvbl9zdGF0ZSI6IjkwNmVmODFiLTMyNTQtNGZjMC05YmI1LTZlZGYyZjFiYzFiYiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiaHR0cDovL2xvY2FsaG9zdDo4MDgwIiwiaHR0cDovL2xvY2FsaG9zdDo5MDkwIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIiwidmlwIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsidnVlLXRlc3QtYXBwIjp7InJvbGVzIjpbImRldmljZSJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJNaWtlIE1pdHRlcmVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoibWlrZSIsImdpdmVuX25hbWUiOiJNaWtlIiwiZmFtaWx5X25hbWUiOiJNaXR0ZXJlciIsImVtYWlsIjoib2ZmaWNlQG1pa2VtaXR0ZXJlci5hdCJ9.
        JoeHL2z78QtnoGUATnk9s3sIAlCLE2fRA-S2nxFEoQ67X8mvItTN8ik9OwhM68-FMrMu2IZQajH4edX4jUCtL-JPYhzhcueNAFBgqZ9JWEcYGlry7O76jntw6woMC8i1q-UrzM0gQzUmsVyUIDKD2jy9eDgO1xkhdvWKsiUwwAL9bUCGYwIYEbsomuizsY1C0wNSxNNbejtxEjBnKkpbklQHEP-v0bUWKpPgoFDzPfnhhH46ISa_gBZElm_TFXAsxU_87NebDT2Qik0HxPOueIZiseAzA6G2T1-ymGwP_HgxMaf9RZfrDbdtyyY1IVW4X6GYzrgGUU59znPUk6dRiQ        
    """.trimIndent().replace("\n","")
}
