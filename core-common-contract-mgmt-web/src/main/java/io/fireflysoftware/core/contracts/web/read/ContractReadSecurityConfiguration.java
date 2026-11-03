/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.contracts.web.read;

import io.fireflysoftware.core.contracts.core.services.*;
import io.fireflysoftware.core.contracts.web.read.impl.AuthenticatedContractReadServiceImpl;
import java.time.Clock;
import org.fireflyframework.security.rs.AccessTokenValidators;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.*;
import reactor.core.publisher.Mono;

/** First deployment scope is two authenticated reads; other contract routes are not an alternate read path. */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(ContractReadSettings.class)
public class ContractReadSecurityConfiguration {
    @Bean AuthenticatedContractReadService authenticatedContractReadService(ContractService contracts,ContractPartyService parties,
            ObjectProvider<PolicyEvaluationPort> policies,ContractReadSettings settings){
        return new AuthenticatedContractReadServiceImpl(contracts,parties,policies,settings,Clock.systemUTC(),System::nanoTime);
    }
    @Bean
    ReactiveJwtDecoder contractReadJwtDecoder(ContractReadSettings settings){
        try {
            settings.requireCurrent(Clock.systemUTC().instant());
            var configured=NimbusReactiveJwtDecoder.withJwkSetUri(settings.jwkSetUri()).build();
            configured.setJwtValidator(AccessTokenValidators.forIssuerAndAudience(settings.issuer(),settings.audience()));return configured;
        } catch(RuntimeException unavailable){return token->Mono.error(new JwtException("Contract read trust is unavailable"));}
    }
    @Bean @Order(-110)
    SecurityWebFilterChain authenticatedContractReads(ServerHttpSecurity http,@Qualifier("contractReadJwtDecoder") ReactiveJwtDecoder decoder){
        return stateless(http).securityMatcher(new OrServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,"/api/v1/contracts/*"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,"/api/v1/contracts/*/parties/filter")))
            .authorizeExchange(rules->rules.anyExchange().authenticated()).oauth2ResourceServer(server->server.jwt(jwt->jwt.jwtDecoder(decoder))).build();
    }
    @Bean @Order(-109)
    SecurityWebFilterChain unsupportedContractRoutes(ServerHttpSecurity http){return stateless(http)
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/contracts","/api/v1/contracts/**","/api/v1/contract-parties","/api/v1/contract-parties/**"))
        .authorizeExchange(rules->rules.anyExchange().denyAll()).exceptionHandling(errors->errors.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.FORBIDDEN))).build();}
    @Bean @Order(-108)
    SecurityWebFilterChain publicContractOpenapi(ServerHttpSecurity http){return stateless(http)
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,"/v3/api-docs","/v3/api-docs.yaml","/v3/api-docs/**"))
        .authorizeExchange(rules->rules.anyExchange().permitAll()).build();}
    private static ServerHttpSecurity stateless(ServerHttpSecurity http){return http.httpBasic(value->value.disable()).formLogin(value->value.disable())
        .csrf(value->value.disable()).securityContextRepository(NoOpServerSecurityContextRepository.getInstance()).requestCache(value->value.requestCache(org.springframework.security.web.server.savedrequest.NoOpServerRequestCache.getInstance()));}
}
