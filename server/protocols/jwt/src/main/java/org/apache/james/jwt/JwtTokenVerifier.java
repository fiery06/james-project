/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jwt;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;

public class JwtTokenVerifier {

    public interface Factory {
        JwtTokenVerifier create();
    }

    public static JwtTokenVerifier create(JwtConfiguration jwtConfiguration) {
        PublicKeyProvider publicKeyProvider = new PublicKeyProvider(jwtConfiguration, new PublicKeyReader());
        return new JwtTokenVerifier(publicKeyProvider);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenVerifier.class);

    private final List<PublicKey> publicKeys;

    public JwtTokenVerifier(PublicKeyProvider pubKeyProvider) {
        this.publicKeys = pubKeyProvider.get();
    }

    public Optional<String> verifyAndExtractLogin(String token) {
        return publicKeys.stream()
            .flatMap(key -> verifyAndExtractLogin(token, key).stream())
            .findFirst();
    }

    public Optional<String> verifyAndExtractLogin(String token, PublicKey key) {
        try {
            String subject = extractLogin(token, key);
            if (Strings.isNullOrEmpty(subject)) {
                throw new MalformedJwtException("'subject' field in token is mandatory");
            }
            return Optional.of(subject);
        } catch (JwtException e) {
            LOGGER.info("Failed Jwt verification", e);
            return Optional.empty();
        }
    }

    private String extractLogin(String token, PublicKey publicKey) throws JwtException {
        Jws<Claims> jws = parseToken(token, publicKey);
        return jws
                .getBody()
                .getSubject();
    }

    public boolean hasAttribute(String attributeName, Object expectedValue, String token) {
       return publicKeys.stream()
           .anyMatch(key -> hasAttribute(attributeName, expectedValue, token, key));
    }

    private boolean hasAttribute(String attributeName, Object expectedValue, String token, PublicKey publicKey) {
        try {
            Jwts
                .parser()
                .require(attributeName, expectedValue)
                .setSigningKey(publicKey)
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            LOGGER.info("Jwt validation failed for claim {} to {}", attributeName, expectedValue, e);
            return false;
        }
    }

    private Jws<Claims> parseToken(String token, PublicKey publicKey) throws JwtException {
        return Jwts
                .parser()
                .setSigningKey(publicKey)
                .parseClaimsJws(token);
    }
}
