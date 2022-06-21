package nc.noumea.mairie.eservicecoretools.keycloak

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.keycloak.KeycloakPrincipal
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.AccessToken
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.core.env.Environment
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Service
import javax.naming.ConfigurationException
import javax.servlet.http.HttpSession
import javax.ws.rs.NotFoundException

@Service
class KeycloakUserService(
    private val environment: Environment,
    private val config: Config,
) {

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

    /**
     * Create a user with the given information.
     * @param sendEmail if true, an email is sent to invite the user to set a password.
     */
    fun addUser(realm: Realm, email: String, firstName: String, lastName: String, sendEmail: Boolean) {
        val realmResource = realmResource(realm, "admin-cli")
        val usersResource: UsersResource = realmResource.users()
        val userRepresentation = UserRepresentation().apply {
            this.email = email
            this.firstName = firstName
            this.lastName = lastName
            isEnabled = true
            isEmailVerified = true
        }
        val createResponse = usersResource.create(userRepresentation)
        val userId = CreatedResponseUtil.getCreatedId(createResponse)
        if (sendEmail) {
            val clientRepresentation = realmResource
                .clients()
                ?.findByClientId(config.resource)
                ?.firstOrNull()
                ?: throw NotFoundException("Application '${config.resource}'")
            usersResource.get(userId).executeActionsEmail(config.resource, clientRepresentation.baseUrl, listOf("UPDATE_PASSWORD"))
        }
    }

    /**
     * Grant a user access to this application.
     */
    fun giveRoleToUser(realm: Realm, username: String, keycloakRole: String) {
        val realmResource = realmResource(realm)

        val clientRepresentation = realmResource
            .clients()
            ?.findByClientId(config.resource)
            ?.firstOrNull()
            ?: throw NotFoundException("Application '${config.resource}'")

        val roleRepresentation = realmResource
            .clients()
            ?.get(clientRepresentation.id)
            ?.roles()
            ?.get(keycloakRole)
            ?.toRepresentation()
            ?: throw NotFoundException("Role '$keycloakRole' for application ${config.resource}")

        val userKeycloak = findByUsername(realm, username)
            ?: throw NotFoundException("User '$username' in realm ${realm.name.toLowerCase()}")

        val userRoles = realmResource.users().get(userKeycloak.id).roles().clientLevel(clientRepresentation.id)
        if (roleRepresentation !in userRoles.listAll()) {
            userRoles.add(listOf(roleRepresentation))
        }
    }

    private fun realmResource(realm: Realm, clientId: String = config.resource) = when (realm) {
        Realm.INTERNE -> interneRealmResource(clientId)
        Realm.EXTERNE -> externeRealmResource(clientId)
    }

    private fun interneRealmResource(clientId: String): RealmResource = keycloakRealmResource(
        config.interneAuthServerUrl,
        config.interneRealm,
        config.interneAdminLogin ?: throw ConfigurationException("Admin configuration is not provided for this realm."),
        config.interneAdminPassword ?: throw ConfigurationException("Admin configuration is not provided for this realm."),
    )

    private fun externeRealmResource(clientId: String): RealmResource = keycloakRealmResource(
        config.externeAuthServerUrl,
        config.externeRealm,
        config.externeAdminLogin ?: throw ConfigurationException("Admin configuration is not provided for this realm."),
        config.externeAdminPassword ?: throw ConfigurationException("Admin configuration is not provided for this realm."),
    )

    private fun keycloakRealmResource(
        authServerUrl: String,
        realm: String,
        adminLogin: String,
        adminPassword: String,
    ): RealmResource {
        val keycloakBuilder = KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm("master")
            .grantType(OAuth2Constants.PASSWORD)
            .clientId("admin-cli")
            .username(adminLogin)
            .password(adminPassword)
        val keycloak = keycloakBuilder.build()
        return keycloak.realm(realm)
    }

}