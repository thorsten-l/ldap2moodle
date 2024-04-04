/*
 * Copyright 2023 Thorsten Ludewig (t.ludewig@gmail.com).
 *
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
package l9g.app.ldap2moodle.client;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLException;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.handler.CryptoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;
import reactor.netty.http.client.HttpClient;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Component
public class MoodleClientFactory
{
  private final static Logger LOGGER
    = LoggerFactory.getLogger(MoodleClientFactory.class);

  @Autowired
  private Config config;

  @Autowired
  private CryptoHandler cryptoHandler;

  @Bean
  public MoodleClient createMoodleClient() throws SSLException
  {
    LOGGER.debug("createMoodleClient");
    var webClientBuilder = WebClient.builder();

    if (config.isMoodleTrustAllCertificates())
    {
      var sslContext = SslContextBuilder.forClient().trustManager(
        InsecureTrustManagerFactory.INSTANCE).build();

      HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(
        sslContext));

      webClientBuilder = webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    HashMap<String,String> defaultUriVariables = new HashMap<>();
    defaultUriVariables.put("wstoken", cryptoHandler.decrypt(config.getMoodleToken()));
    defaultUriVariables.put("moodlewsrestformat", "json");
    
    WebClient webClient = webClientBuilder
      .baseUrl(config.getMoodleBaseUrl())
      .defaultUriVariables(defaultUriVariables)
      .build();

    HttpServiceProxyFactory factory
      = HttpServiceProxyFactory.builder(
        WebClientAdapter.forClient(webClient)).build();

    return factory.createClient(MoodleClient.class);
  }
}
