/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.metatron.discovery.common.oauth.token.service;

import app.metatron.discovery.common.oauth.OauthProperties;
import app.metatron.discovery.common.oauth.token.cache.CachedToken;
import app.metatron.discovery.common.oauth.token.cache.TokenCacheRepository;
import app.metatron.discovery.util.HttpUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;

import javax.servlet.http.HttpServletRequest;

public class CustomDefaultTokenService extends DefaultTokenServices {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomDefaultTokenService.class);

  @Autowired
  OauthProperties oauthProperties;

  @Autowired
  TokenCacheRepository tokenCacheRepository;

  @Autowired
  HttpServletRequest httpServletRequest;

  @Autowired
  TokenStore tokenStore;

  @Override
  public OAuth2Authentication loadAuthentication(String accessTokenValue) throws AuthenticationException, InvalidTokenException {
    try {
      if (accessTokenValue.indexOf("|") < 0) {
        accessTokenValue = accessTokenValue + "|" + HttpUtils.getClientIp(httpServletRequest);
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
    }


    if (accessTokenValue.indexOf("|") > -1) {
      String userHost = accessTokenValue.split("\\|")[1];
      accessTokenValue = accessTokenValue.split("\\|")[0];
      LOGGER.debug("loadAuthentication() - accessToken: {}, userHost: {}", accessTokenValue, userHost);

      // cannot refresh token not in whitelist cache
      if(oauthProperties.getTimeout() > -1){
        OAuth2AccessToken oAuth2AccessToken = this.readAccessToken(accessTokenValue);

        // getting username, clientid, clientip
        String username = (String) oAuth2AccessToken.getAdditionalInformation().get("user_name");
        String clientId = this.getClientId(accessTokenValue);

        if (username != null && !oAuth2AccessToken.isExpired()) {
          // getting whitelist in cache
          CachedToken cachedToken = tokenCacheRepository.getCachedToken(username, clientId);
          if (cachedToken != null) {
            String cachedUserHost = cachedToken.getUserIp();
            // if not matched in whitelist cache, throw exception
            if (!userHost.equals(cachedUserHost)) {
              LOGGER.info("Cached Whitelist token's ip ({}) is not matched userIp ({})", cachedUserHost, userHost);
              throw new InvalidTokenException("User ip is not in whitelist.");
            }
          }
        }
      }
    }

    return super.loadAuthentication(accessTokenValue);
  }

}
