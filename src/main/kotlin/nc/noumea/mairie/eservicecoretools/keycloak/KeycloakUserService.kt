package nc.noumea.mairie.eservicecoretools.keycloak

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service


@Service
open class KeycloakUserService {

    @Autowired
    private val environment: Environment? = null

    @Value("\${server.proxy.url}")
    private lateinit var proxyKeycloakUrl: String

    @Value("\${server.proxy.port}")
    private lateinit var proxyKeycloakPort: String

    @Value("\${keycloak.resource}")
    private lateinit var resource: String

    @Value("\${tenant.interne.auth-server-url}")
    private lateinit var interneAuthServerUrl: String

    @Value("\${tenant.interne.realm}")
    private lateinit var interneRealm: String

    @Value("\${tenant.interne.secret}")
    private lateinit var interneClientSecret: String

    @Value("\${tenant.interne.admin.login}")
    private lateinit var interneAdminLogin: String

    @Value("\${tenant.interne.admin.password}")
    private lateinit var interneAdminPassword: String

    @Value("\${tenant.externe.auth-server-url}")
    private lateinit var externeAuthServerUrl: String

    @Value("\${tenant.externe.realm}")
    private lateinit var externeRealm: String

    @Value("\${tenant.externe.secret}")
    private lateinit var externeClientSecret: String

    @Value("\${tenant.externe.admin.login}")
    private lateinit var externeAdminLogin: String

    @Value("\${tenant.externe.admin.password}")
    private lateinit var externeAdminPassword: String

    fun findByUuid(realm: Realm, uuid: String): UserRepresentation? =
        realmResource(realm).users()?.get(uuid)?.toRepresentation()

    fun findByEmail(realm: Realm, email: String): UserRepresentation? =
        realmResource(realm).users()?.search(email)?.firstOrNull()

    fun findByUsername(realm: Realm, username: String): UserRepresentation? {
        // admin user needs at least "manage-users, view-clients, view-realm, view-users" roles for "realm-management"

        return realmResource(realm).users().search(username).firstOrNull()

        /*
        val usersResource = realmResource.users()
        // On récupére l'utilisateur sur keycloak pour avoir son id
        val listeUserKeycloak = usersResource.search(username)
        if (listeUserKeycloak.isNullOrEmpty()) {
            return null
        }

        val userKeycloak = listeUserKeycloak.first()
        val userResource = realmResource.users().get(userKeycloak.id)
        val clientRepresentation = realmResource.clients().findByClientId(resource)[0]
        var clientRoleRepresentation = RoleRepresentation()

        val userRole = userResource.roles().all.clientMappings[resource]?.mappings?.first()?.name

        if (userRole != Role.ROLE_UTILISATEUR.libelle.uppercase()) {
            userResource.roles().clientLevel(clientRepresentation.id).listAvailable().forEach lit@{
                if (it.name == Role.ROLE_UTILISATEUR.libelle.uppercase()) {
                    clientRoleRepresentation = it
                    return@lit
                }
            }
        }

        //On attribut un nouveau rôle du client
        try {
            userResource.roles().clientLevel(clientRepresentation.id).add(listOf(clientRoleRepresentation))
        } catch (e: Exception) {
            log.warn(e.message)
        }

        return userResource.toRepresentation()
        */
    }

    private fun realmResource(realm: Realm): RealmResource {
        val realmResource = when (realm) {
            Realm.INTERNE -> getInterneKeycloakRealmResource()
            Realm.EXTERNE -> getExterneKeycloakRealmResource()
        }
        return realmResource
    }

    private fun getInterneKeycloakRealmResource(): RealmResource {
        val keycloakBuilder = KeycloakBuilder.builder()
            .serverUrl(interneAuthServerUrl)
            .realm(interneRealm)
            .grantType(OAuth2Constants.PASSWORD)
            .clientId(resource)
            .clientSecret(interneClientSecret)
            .username(interneAdminLogin)
            .password(interneAdminPassword)
        if (!this.environment!!.getActiveProfiles().contains("dev")) {
            keycloakBuilder.resteasyClient(ResteasyClientBuilder().defaultProxy(proxyKeycloakUrl, proxyKeycloakPort.toInt()).build())
        }
        val keycloak = keycloakBuilder.build()
        return keycloak.realm(interneRealm)
    }

    private fun getExterneKeycloakRealmResource(): RealmResource {
        val keycloakBuilder = KeycloakBuilder.builder()
            .serverUrl(externeAuthServerUrl)
            .realm(externeRealm)
            .grantType(OAuth2Constants.PASSWORD)
            .clientId(resource)
            .clientSecret(externeClientSecret)
            .username(externeAdminLogin)
            .password(externeAdminPassword)
        if (!this.environment!!.getActiveProfiles().contains("dev")) {
            keycloakBuilder.resteasyClient(ResteasyClientBuilder().defaultProxy(proxyKeycloakUrl, proxyKeycloakPort.toInt()).build())
        }
        val keycloak = keycloakBuilder.build()
        return keycloak.realm(externeRealm)
    }

}