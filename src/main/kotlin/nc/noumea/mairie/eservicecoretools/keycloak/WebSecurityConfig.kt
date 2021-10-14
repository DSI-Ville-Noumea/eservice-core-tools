package nc.noumea.mairie.eservicecoretools.keycloak

import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver
import org.keycloak.adapters.springsecurity.KeycloakConfiguration
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationEntryPoint
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@KeycloakConfiguration
@EnableAutoConfiguration
@ComponentScan
open class WebSecurityConfig : KeycloakWebSecurityConfigurerAdapter() {

    companion object {
        val LOGOUT_URI = "/logout"
    }

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

    @Autowired
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        val keycloakAuthenticationProvider = keycloakAuthenticationProvider()
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(SimpleAuthorityMapper())
        auth.authenticationProvider(keycloakAuthenticationProvider)
    }

    @Bean
    @Primary
    open fun keycloakConfigResolver(): KeycloakSpringBootConfigResolver {
        return MultitenantConfigResolver()
    }

    @Bean
    override fun sessionAuthenticationStrategy(): SessionAuthenticationStrategy {
        return RegisterSessionAuthenticationStrategy(SessionRegistryImpl())
    }

    override fun authenticationEntryPoint(): AuthenticationEntryPoint? {
        // Les requêtes non authententifiées sont redirigées vers Nouméa Connect (302 Redirect)
        // Sauf les requêtes de l'API ZK : 401 Unauthorized, ZK se chargera de recharger la page.
        return KeycloakAuthenticationEntryPoint(adapterDeploymentContext(), AntPathRequestMatcher("/zkau", "POST"))
    }

    override fun configure(http: HttpSecurity) {
        super.configure(http)

        // Ajouté pour que les downloads fonctionnent sur mac #60229 : FO-G : Impossible de voir les documents sur MAC MINI
        http.headers().frameOptions().sameOrigin()

        http.authorizeRequests().antMatchers("/actuator/**").hasIpAddress(actuatorAuthorizedIp)
        http.authorizeRequests().anyRequest().authenticated()
        http.httpBasic().disable()

        // TODO vérifier, ça pourrait être utilisé, voir KeycloakCsrfRequestMatcher
        http.csrf().disable()

        // ATTENTION: pour une raison inconnue, si on enlève le logoutSuccessHandler, l'accès à la page principale n'est pas sécurisée ( '/' )
        http.logout().logoutUrl(LOGOUT_URI).logoutSuccessHandler(SimpleUrlLogoutSuccessHandler())
    }
}