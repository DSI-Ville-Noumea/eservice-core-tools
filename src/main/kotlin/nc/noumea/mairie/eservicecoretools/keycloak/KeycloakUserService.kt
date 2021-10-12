package nc.noumea.mairie.eservicecoretools.keycloak

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.keycloak.KeycloakPrincipal
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.AccessToken
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.core.env.Environment
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import javax.servlet.http.HttpSession

@Service class KeycloakUserService(
    private val environment: Environment,
    private val config: WebSecurityConfig
) {

    private val interneRealmResource: RealmResource by lazy { keycloakRealmResource(
        config.interneAuthServerUrl,
        config.interneRealm,
        config.interneClientSecret,
        config.interneAdminLogin,
        config.interneAdminPassword,
    ) }

    private val externeRealmResource: RealmResource by lazy { keycloakRealmResource(
        config.externeAuthServerUrl,
        config.externeRealm,
        config.externeClientSecret,
        config.externeAdminLogin,
        config.externeAdminPassword,
    ) }

    /**
     * The URI to redirect in order to logout.
     */
    val logoutUri = WebSecurityConfig.LOGOUT_URI

    /**
     * Request the keycloak server to find a user.
     */
    fun findByUuid(realm: Realm, uuid: String): UserRepresentation? =
        realmResource(realm).users()?.get(uuid)?.toRepresentation()

    /**
     * Request the keycloak server to find a user.
     */
    fun findByEmail(realm: Realm, email: String): UserRepresentation? =
        realmResource(realm).users()?.search(email)?.firstOrNull()

    /**
     * Request the keycloak server to find a user.
     */
    fun findByUsername(realm: Realm, username: String): UserRepresentation? =
        realmResource(realm).users().search(username).firstOrNull()

    /**
     * The account administration URL.
     */
    fun myAccountUrl(realm: Realm) = when (realm) {
        Realm.INTERNE -> "${config.interneAuthServerUrl}/realms/${config.interneRealm}/account/?referrer=${config.resource}"
        Realm.EXTERNE -> "${config.externeAuthServerUrl}/realms/${config.externeRealm}/account/?referrer=${config.resource}"
    }.replace("//", "/") // fix potential trailing slash issue

    /**
     * Provides information about the user logged in the given http session.
     */
    fun getCurrentToken(httpSession: HttpSession): AccessToken? {
        val securityContext = httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) as SecurityContext?
        val keycloakPrincipal = securityContext?.authentication?.principal as KeycloakPrincipal<*>?
        return keycloakPrincipal?.keycloakSecurityContext?.token
    }

    /**
     * Same as getCurrentToken, but with additional information requested from the keycloak server.
     */
    fun getCurrent(httpSession: HttpSession): UserRepresentation? = getCurrentToken(httpSession)?.let {
        // TODO meilleure façon de différentier (header realm + defaultRealm ?)
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
            .clientId(config.resource)
            .clientSecret(clientSecret)
            .username(adminLogin)
            .password(adminPassword)
        if (!this.environment.activeProfiles.contains("dev")) {
            keycloakBuilder.resteasyClient(ResteasyClientBuilder().defaultProxy(config.proxyKeycloakUrl, config.proxyKeycloakPort.toInt()).build())
        }
        val keycloak = keycloakBuilder.build()
        return keycloak.realm(realm)
    }

}