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

    // Map qui garde les config de chaque tenant en cache pour ne pas
    // avoir à les re-créer à chaque fois
    var cache = mutableMapOf<String, KeycloakDeployment>()

    // dispatch la requete au bon tenant pour vérifier l'auth. Par defaut, doit
    // rediriger sur le royaume interne ( = royaume pluggé sur le LDAP )
    override fun resolve(request: HttpFacade.Request): KeycloakDeployment {

        // En local le header n'est pas setté, en prod/qual il est setté automatiquement par les reverse proxy
        val realm = request.getHeader("Realm")
        if (realm == null) {
            logger.debug("No 'Realm' header")
            if (defaultRealm == "externe") {
                //se configure avec les infos de connexion (tenant.) du application properties
                return getDeploiementExterne()
            } else {
                return getDeploiementInterne()
            }
        }

        logger.debug("Got header 'Realm': $realm")

        return if ("interne" == realm) {
            getDeploiementInterne()
        } else {
            getDeploiementExterne()
        }
    }

    fun getDeploiementInterne(): KeycloakDeployment {
        if (!cache.containsKey(interneRealm)) {
            cache[interneRealm] = createKeycloakDeployment(interneRealm, resource, interneAuthServerUrl, interneClientSecret, proxyUrl)
        }
        logger.debug("auth on realm interne")
        return cache[interneRealm]!!
    }

    fun getDeploiementExterne(): KeycloakDeployment {
        if (!cache.containsKey(externeRealm)) {
            cache[externeRealm] = createKeycloakDeployment(externeRealm, resource, externeAuthServerUrl, externeClientSecret, proxyUrl)
        }
        logger.debug("auth on realm externe")
        return cache[externeRealm]!!
    }

    final fun createKeycloakDeployment(realm: String, resource: String, authServerUrl: String, clientSecret: String, proxyUrl: String): KeycloakDeployment {
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