package nc.noumea.mairie.eservicecoretools.keycloak

import org.keycloak.adapters.KeycloakConfigResolver
import org.keycloak.adapters.KeycloakDeployment
import org.keycloak.adapters.KeycloakDeploymentBuilder
import org.keycloak.adapters.spi.HttpFacade
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver
import org.keycloak.representations.adapters.config.AdapterConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import javax.ws.rs.ForbiddenException

@Component
internal class MultitenantConfigResolver : KeycloakSpringBootConfigResolver(), KeycloakConfigResolver {

    private val logger = LoggerFactory.getLogger(MultitenantConfigResolver::class.java)

    @Autowired private lateinit var environment: Environment
    @Autowired private lateinit var configuration: WebSecurityConfig

    private val deploiementInterne by lazy {
        createKeycloakDeployment(configuration.interneRealm, configuration.resource, configuration.interneAuthServerUrl, configuration.interneClientSecret, configuration.proxyUrl)
    }

    private val deploiementExterne by lazy {
        createKeycloakDeployment(configuration.externeRealm, configuration.resource, configuration.externeAuthServerUrl, configuration.externeClientSecret, configuration.proxyUrl)
    }

    override fun resolve(request: HttpFacade.Request): KeycloakDeployment {
        val requestRealm = request.getHeader("Realm")
        val realm = if (requestRealm != null) {
            logger.debug("Using request header Realm.")
            requestRealm
        } else {
            logger.debug("No request header Realm.")
            configuration.defaultRealm
        }
        logger.debug("Using keycloak realm: '$realm'")

        return when (realm.toLowerCase()) {
            Realm.INTERNE.name.toLowerCase() -> deploiementInterne
            Realm.EXTERNE.name.toLowerCase() -> deploiementExterne
            else -> throw ForbiddenException("Invalid keycloak realm: '$realm'")
        }
    }

    private fun createKeycloakDeployment(realm: String, resource: String, authServerUrl: String, clientSecret: String, proxyUrl: String): KeycloakDeployment {
        val ac = AdapterConfig()
        ac.realm = realm
        ac.resource = resource
        ac.authServerUrl = authServerUrl
        ac.sslRequired = "external"
        if (!this.environment.activeProfiles.contains("dev")) {
            ac.proxyUrl = proxyUrl
        }
        ac.credentials = mapOf("secret" to clientSecret)
        return KeycloakDeploymentBuilder.build(ac)
    }
}