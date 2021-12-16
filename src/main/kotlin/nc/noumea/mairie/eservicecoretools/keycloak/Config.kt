package nc.noumea.mairie.eservicecoretools.keycloak

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class Config {

    @Value("\${server.proxy.url}") lateinit var proxyKeycloakUrl: String
    @Value("\${server.proxy.port}") lateinit var proxyKeycloakPort: String
    @Value("\${keycloak.resource}") lateinit var resource: String
    @Value("\${tenant.interne.auth-server-url}") lateinit var interneAuthServerUrl: String
    @Value("\${tenant.interne.realm}") lateinit var interneRealm: String
    @Value("\${tenant.interne.secret}") lateinit var interneClientSecret: String
    @Value("\${tenant.interne.admin.login:#{null}}") var interneAdminLogin: String? = null
    @Value("\${tenant.interne.admin.password:#{null}}") var interneAdminPassword: String? = null
    @Value("\${tenant.externe.auth-server-url}") lateinit var externeAuthServerUrl: String
    @Value("\${tenant.externe.realm}") lateinit var externeRealm: String
    @Value("\${tenant.externe.secret}") lateinit var externeClientSecret: String
    @Value("\${tenant.externe.admin.login:#{null}}") var externeAdminLogin: String? = null
    @Value("\${tenant.externe.admin.password:#{null}}") var externeAdminPassword: String? = null
    @Value("\${keycloak.proxy-url}") lateinit var proxyUrl: String
    @Value("\${custom.keycloak.default-realm}") lateinit var defaultRealm: String
    @Value("\${actuator-authorized-ip:172.16.0.0/16}") lateinit var actuatorAuthorizedIp: String
}