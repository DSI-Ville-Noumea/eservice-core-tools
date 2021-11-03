# eservice core tools

## Migration d'une appli pour l'auhentification
Si une appli contient les classes suivantes :
* MultitenantConfigResolver
* WebSecurityConfig
* KeycloakService

Alors il est souhaitable de la faire évoluer pour utiliser eservice-core-tools à la place.

Exemple sur subnea : https://github.com/DSI-Ville-Noumea/subnea/pull/467/files
Exemple sur allodb : https://github.com/DSI-Ville-Noumea/allodb/pull/773/files


### Etapes
#### 1) Retirer les dépendances vers keycloak

Exemple shinigami :
   
#### 2) Recompiler (mv clean compile)
Il devrait y avoir des erreurs de compilation, notamment sur les classes suivantes :
 - MultitenantConfigResolver
 - WebSecurityConfig
 - PrincipalHelper
 - KeycloakService
 - UrlController

Exemple shinigami :

#### 3) Supprimer les classes suivantes elles seront remplacées par eservice-core-tools :
 - MultitenantConfigResolver
 - WebSecurityConfig
 - KeycloakService

Exemple shinigami :

#### 4) Ajouter la dépendance vers eservice-core-tools dans le pom.xml et recompiler
```
<eservice-core-tools.version>1.1.0</eservice-core-tools.version>
```
```
<dependency>
   <groupId>nc.noumea.mairie</groupId>
   <artifactId>eservice-core-tools</artifactId>
   <version>${eservice-core-tools.version}</version>
</dependency>
```
```
$ mvn clean compile
```
Exemple shinigami :

#### 5) Corriger les erreurs de compilation
- `KeycloakUserService` devrait pouvoir remplacer les usages de `KeycloakService`
- Simplifier le `PrincipalHelper` en utilisant `KeycloakUserService`.

#### 5) Import de la configuration
- Ajouter l'annotation suivante à la classe Application : 
```
@Import(WebSecurityConfig::class)
class Application {
```
- Vérifier que les éléments suivants sont présents dans `application.properties`:
```
keycloak.proxy-url
keycloak.resource
custom.keycloak.default-realm
tenant.interne.admin.login
tenant.interne.admin.password
tenant.interne.auth-server-url
tenant.interne.realm
tenant.interne.secret
tenant.externe.auth-server-url
tenant.externe.realm
tenant.externe.secret
tenant.externe.admin.login
tenant.externe.admin.password
```
Exemple shinigami :

#### 6) Adapter le logout
- Partout où est l'URL `logoutKeycloak` est utilisée, utiliser  `KeycloakUserService.logoutUrl` à la place ou bien `logout`.
- Supprimer la fonction `UrlController.logout()`

Exemple shinigami :

#### 7) Bien tester
- connexion en tant qu'usager
- connexion en tant qu'agent mairie
- deconnexion
- différents rôles
