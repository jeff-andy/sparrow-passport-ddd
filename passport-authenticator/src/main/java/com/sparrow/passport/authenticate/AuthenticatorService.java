/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sparrow.passport.authenticate;

import com.sparrow.core.spi.JsonFactory;
import com.sparrow.cryptogram.Base64;
import com.sparrow.cryptogram.Hmac;
import com.sparrow.exception.Asserts;
import com.sparrow.json.Json;
import com.sparrow.passport.domain.DomainRegistry;
import com.sparrow.passport.domain.entity.SecurityPrincipalEntity;
import com.sparrow.passport.protocol.enums.PassportError;
import com.sparrow.protocol.BusinessException;
import com.sparrow.protocol.LoginUser;
import com.sparrow.protocol.LoginUserStatus;
import com.sparrow.support.AbstractAuthenticatorService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Named;
import org.springframework.beans.factory.annotation.Value;

@Named
public class AuthenticatorService extends AbstractAuthenticatorService {

    private Json json = JsonFactory.getProvider();

    @Value("${auth.encrypt_key}")
    private String encryptKey;

    @Inject
    private DomainRegistry domainRegistry;

    @Override
    protected String getEncryptKey() {
        if (encryptKey != null) {
            return encryptKey;
        }
        synchronized (this) {
            if (encryptKey != null) {
                return encryptKey;
            }
            this.encryptKey = System.getenv("ENCRYPT_KEY");
        }
        return this.encryptKey;
    }

    @Override
    protected String getDecryptKey() {
        return this.getEncryptKey();
    }

    @Override
    protected String sign(LoginUser loginUser, String secretKey) {
        String userInfo = this.json.toString(loginUser);
        String signature = Hmac.getInstance().getSHA1Base64(userInfo,
            this.encryptKey);
        return Base64.encodeBytes(userInfo.getBytes(StandardCharsets.US_ASCII)) + "." + signature;
    }

    @Override
    protected LoginUser verify(String token, String secretKey) throws BusinessException {
        String[] tokens = token.split("\\.");
        Asserts.isTrue(tokens.length != 2, PassportError.USER_TOKEN_ERROR);
        String userInfo = tokens[0];
        String signature = tokens[1];
        try {
            userInfo = new String(Base64.decode(userInfo), StandardCharsets.US_ASCII);
            String signatureOld = Hmac.getInstance().getSHA1Base64(userInfo,
                this.encryptKey);
            if (signature.equals(signatureOld)) {
                return this.json.parse(userInfo, LoginUser.class);
            }
            throw new BusinessException(PassportError.USER_TOKEN_ERROR);
        } catch (IOException e) {
            throw new BusinessException(PassportError.USER_TOKEN_ERROR);
        }
    }

    @Override
    protected void setUserStatus(LoginUser loginUser, LoginUserStatus loginUserStatus) {
//
    }

    @Override
    protected LoginUserStatus getUserStatus(Long userId) {
        //todo 从缓存中获取
        return null;
    }

    @Override
    protected LoginUserStatus getUserStatusFromDB(Long userId) {
        SecurityPrincipalEntity securityPrincipal = this.domainRegistry.getSecurityPrincipalRepository().findByUserId(userId);
        return new LoginUserStatus(securityPrincipal.getStatus(), 0L);
    }

    @Override
    protected void renewal(LoginUser loginUser, LoginUserStatus loginUserStatus) {
        //如果过期时间小于30分钟，延长1小时
        if (loginUserStatus.getExpireAt() - System.currentTimeMillis() < Duration.ofMinutes(30).toMillis())
            loginUserStatus.setExpireAt(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
    }
}
