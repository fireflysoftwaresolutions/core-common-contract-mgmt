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

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Explicit single-dataset deployment identity; ContractDTO carries no tenant identity. */
@ConfigurationProperties("contracts.authenticated-read")
public record ContractReadSettings(boolean enabled,String issuer,String audience,String jwkSetUri,String allowedClientId,
        String platformTenant,UUID resourceTenantId,UUID configurationBindingId,long configurationBindingVersion,
        String configurationBindingDigest,UUID policyholderRoleId,Instant notAfter,boolean allowLoopbackHttp) {
    public void requireCurrent(Instant now) {
        if(!enabled || !text(issuer) || !text(audience) || !text(jwkSetUri) || !text(allowedClientId) || !text(platformTenant)
            || resourceTenantId==null || configurationBindingId==null || configurationBindingVersion<1 || policyholderRoleId==null
            || configurationBindingDigest==null || !configurationBindingDigest.matches("[0-9a-f]{64}") || notAfter==null || !now.isBefore(notAfter)
            || !trustedEndpoint(issuer) || !trustedEndpoint(jwkSetUri))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Contract read is unavailable");
    }
    private boolean trustedEndpoint(String raw) {
        try {var uri=URI.create(raw);return uri.getHost()!=null && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null
            && ("https".equals(uri.getScheme()) || allowLoopbackHttp && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()));}
        catch(IllegalArgumentException invalid){return false;}
    }
    private static boolean text(String value){return value!=null&&!value.isBlank()&&value.length()<=1024&&value.equals(value.strip())&&value.codePoints().noneMatch(Character::isISOControl);}
    @Override public String toString(){return "ContractReadSettings[redacted]";}
}
