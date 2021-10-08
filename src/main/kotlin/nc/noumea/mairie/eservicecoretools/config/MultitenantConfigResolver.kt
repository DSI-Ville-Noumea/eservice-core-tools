package nc.noumea.mairie.eservicecoretools.config

import org.keycloak.adapters.KeycloakConfigResolver
import org.keycloak.adapters.KeycloakDeployment
import org.keycloak.adapters.KeycloakDeploymentBuilder
import org.keycloak.adapters.spi.HttpFacade
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver
import org.keycloak.representations.adapters.config.AdapterConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import javax.ws.rs.ForbiddenException

@Component
class MultitenantConfigResolver : KeycloakSpringBootConfigResolver(), KeycloakConfigResolver {

    private val logger = LoggerFactory.getLogger(MultitenantConfigResolver::class.java)

    @Value("\${keycloak.proxy-url}")
    private lateinit var proxyUrl: String

    @Value("\${keycloak.resource}")
    private lateinit var resource: String

    @Value("\${tenant.interne.auth-server-url}")
    private lateinit var interneAuthServerUrl: String

    @Value("\${tenant.interne.realm}")
    private lateinit var interneRealm: String

    @Value("\${tenant.interne.secret}")
    private lateinit var interneClientSecret: String

    @Value("\${tenant.externe.auth-server-url}")
    private lateinit var externeAuthServerUrl: String

    @Value("\${tenant.externe.realm}")
    private lateinit var externeRealm: String

    @Value("\${tenant.externe.secret}")
    private lateinit var externeClientSecret: String

    @Value("\${custom.keycloak.default-realm}")
    private lateinit var defaultRealm: String

    @Autowired
    private val environment: Environment? = null

    private val deploiementInterne by lazy {
        createKeycloakDeployment(interneRealm, resource, interneAuthServerUrl, interneClientSecret, proxyUrl)
    }

    private val deploiementExterne by lazy {
        createKeycloakDeployment(externeRealm, resource, externeAuthServerUrl, externeClientSecret, proxyUrl)
    }

    override fun resolve(request: HttpFacade.Request): KeycloakDeployment {
        val requestRealm = request.getHeader("Realm")
        val realm = if (requestRealm != null) {
            logger.debug("Using request header Realm.")
            requestRealm
        } else {
            logger.debug("No request header Realm.")
            defaultRealm
        }
        logger.debug("Using keycloak realm: '$realm'")

        when (realm) {
            "interne" -> return deploiementInterne
            "externe" -> return deploiementExterne
            else -> throw ForbiddenException("Invalid keycloak realm: '$realm'")
        }
    }

    private fun createKeycloakDeployment(realm: String, resource: String, authServerUrl: String, clientSecret: String, proxyUrl: String): KeycloakDeployment {
        val ac = AdapterConfig()
        ac.realm = realm
        ac.resource = resource
        ac.authServerUrl = authServerUrl
        ac.sslRequired = "external"
        if (!this.environment!!.getActiveProfiles().contains("dev")) {
            ac.proxyUrl = proxyUrl
        }
        ac.credentials = mapOf("secret" to clientSecret)
        return KeycloakDeploymentBuilder.build(ac)
    }
}