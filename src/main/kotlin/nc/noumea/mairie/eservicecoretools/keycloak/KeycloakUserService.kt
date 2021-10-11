package nc.noumea.mairie.eservicecoretools.keycloak

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.keycloak.KeycloakPrincipal
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.AccessToken
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import javax.servlet.http.HttpSession


@Service
open class KeycloakUserService {

    @Autowired private lateinit var environment: Environment
    @Autowired private lateinit var configuration: WebSecurityConfig

    private val interneRealmResource: RealmResource by lazy { keycloakRealmResource(
        configuration.interneAuthServerUrl,
        configuration.interneRealm,
        configuration.interneClientSecret,
        configuration.interneAdminLogin,
        configuration.interneAdminPassword,
    )}

    private val externeRealmResource: RealmResource by lazy { keycloakRealmResource(
        configuration.externeAuthServerUrl,
        configuration.externeRealm,
        configuration.externeClientSecret,
        configuration.externeAdminLogin,
        configuration.externeAdminPassword,
    )}

    fun findByUuid(realm: Realm, uuid: String): UserRepresentation? = realmResource(realm).users()?.get(uuid)?.toRepresentation()
    fun findByEmail(realm: Realm, email: String): UserRepresentation? = realmResource(realm).users()?.search(email)?.firstOrNull()
    fun findByUsername(realm: Realm, username: String): UserRepresentation? = realmResource(realm).users().search(username).firstOrNull()

    fun myAccountUrl(realm: Realm)  = when (realm) {
        Realm.INTERNE -> "${configuration.interneAuthServerUrl}/realms/${configuration.interneRealm}/account/?referrer=${configuration.resource}"
        Realm.EXTERNE -> "${configuration.externeAuthServerUrl}/realms/${configuration.externeRealm}/account/?referrer=${configuration.resource}"
    }.replace("//", "/") // fix potential trailing slash issue

    fun getCurrentToken(httpSession: HttpSession): AccessToken? {
        val securityContext = httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) as SecurityContext?
        val keycloakPrincipal = securityContext?.authentication?.principal as KeycloakPrincipal<*>?
        return keycloakPrincipal?.keycloakSecurityContext?.token
    }

    fun getCurrent(httpSession: HttpSession): UserRepresentation? = getCurrentToken(httpSession)?.let {
        val realm = if (it.email?.endsWith("ville-noumea.nc") == true) Realm.INTERNE else Realm.EXTERNE
        return findByUuid(realm, it.subject)
    }

    private fun realmResource(realm: Realm) = when (realm) {
        Realm.INTERNE -> interneRealmResource
        Realm.EXTERNE -> externeRealmResource
    }

    private fun keycloakRealmResource(authServerUrl: String, realm: String, clientSecret: String, adminLogin: String, adminPassword: String): RealmResource {
        val keycloakBuilder = KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm(realm)
            .grantType(OAuth2Constants.PASSWORD)
            .clientId(configuration.resource)
            .clientSecret(clientSecret)
            .username(adminLogin)
            .password(adminPassword)
        if (!this.environment.activeProfiles.contains("dev")) {
            keycloakBuilder.resteasyClient(ResteasyClientBuilder().defaultProxy(configuration.proxyKeycloakUrl, configuration.proxyKeycloakPort.toInt()).build())
        }
        val keycloak = keycloakBuilder.build()
        return keycloak.realm(realm)
    }

}