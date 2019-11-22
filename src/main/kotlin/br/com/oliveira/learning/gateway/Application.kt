package br.com.oliveira.learning.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactory
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec
import org.springframework.cloud.gateway.route.builder.PredicateSpec
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.server.SecurityWebFilterChain


@SpringBootApplication
class Application {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<Application>(*args)
        }
    }

    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator? {
        return builder.routes()
                .route("path_route") { r: PredicateSpec ->
                    r.path("/get")
                            .uri("http://httpbin.org")
                }
                .route("host_route") { r: PredicateSpec ->
                    r.host("*.myhost.org")
                            .uri("http://httpbin.org")
                }
                .route("rewrite_route") { r: PredicateSpec ->
                    r.path("/re").and().host("*.rewrite.org")
                            /*.filters { f: GatewayFilterSpec -> f.rewritePath("/foo/(?<segment>.*)", "/\${segment}") }*/
                            .filters { f: GatewayFilterSpec -> f.rewritePath("/foo/1", "/2") }
                            .uri("http://httpbin.org")
                }
                .route("hystrix_route") { r: PredicateSpec ->
                    r.host("*.hystrix.org")
                            .filters { f: GatewayFilterSpec -> f.hystrix { c: HystrixGatewayFilterFactory.Config -> c.name = "slowcmd" } }
                            .uri("http://httpbin.org")
                }
                .route("hystrix_fallback_route") { r: PredicateSpec ->
                    r.host("*.hystrixfallback.org")
                            .filters { f: GatewayFilterSpec -> f.hystrix { c: HystrixGatewayFilterFactory.Config -> c.setName("slowcmd").setFallbackUri("forward:/hystrixfallback") } }
                            .uri("http://httpbin.org")
                }
                .route("limit_route") { r: PredicateSpec ->
                    r.host("*.limited.org").and().path("/anything/**")
                            .filters { f: GatewayFilterSpec -> f.requestRateLimiter { c: RequestRateLimiterGatewayFilterFactory.Config -> c.rateLimiter = redisRateLimiter() } }
                            .uri("http://httpbin.org")
                }
                .build()
    }

    @Bean
    fun redisRateLimiter(): RedisRateLimiter {
        return RedisRateLimiter(1, 2)
    }

    @Bean
    @Throws(Exception::class)
    fun springWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http.httpBasic().and()
                .csrf().disable()
                .authorizeExchange()
                .pathMatchers("/anything/**").authenticated()
                .anyExchange().permitAll()
                .and()
                .build()
    }

    @Bean
    fun reactiveUserDetailsService(): MapReactiveUserDetailsService {
        val user: UserDetails = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER").build()
        return MapReactiveUserDetailsService(user)
    }
}


