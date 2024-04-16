/*
 * Copyright 2024 Thorsten Ludewig (t.ludewig@gmail.com).
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
package l9g.app.ldap2moodle.services;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.handler.CryptoHandler;
import l9g.app.ldap2moodle.model.MoodleAnonymousUser;
import l9g.app.ldap2moodle.model.MoodleRole;
import l9g.app.ldap2moodle.model.MoodleUser;
import l9g.app.ldap2moodle.model.MoodleUsersResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
public class MoodleService
{
 
  
  private final static Logger LOGGER
    = LoggerFactory.getLogger(MoodleService.class);

  final private RestTemplate restTemplate = new RestTemplate();

  final private String wstoken;

  final private Config config;

  @Autowired
  public MoodleService(Config config, CryptoHandler cryptoHandler)
  {
    this.wstoken = cryptoHandler.decrypt(config.getMoodleToken());
    this.config = config;
  }

  record UserCreateResponse(int id, String username) {}
  record UsersCreateResponse(List<UserCreateResponse> list) {}  

  record UsersCreateRequest(List<MoodleUser> list) {}    
  
  /**
   * create URI with parameters
   * @param wsfunction
   * @param parameters
   * @return Uri object
   */
  private URI uriBuilder(String wsfunction,
    LinkedHashMap<String, String> parameters)
  {
    UriComponentsBuilder builder = UriComponentsBuilder
      .fromHttpUrl(config.getMoodleBaseUrl() + "/webservice/rest/server.php")
      .queryParam("wstoken", wstoken)
      .queryParam("moodlewsrestformat", "json")
      .queryParam("wsfunction", wsfunction);

    if (parameters != null && !parameters.isEmpty())
    {
      parameters.forEach((key, value) -> builder.queryParam(key, value));
    }

    UriComponents uriComponents = builder.build().encode();
    LOGGER.debug("uri={}", uriComponents.toUriString());

    return uriComponents.toUri();
  }


  
  /**
   * get all users authenticated by LDAP/OICD from Moodle
   * 
   * @return 
   */
  public List<MoodleUser> users()
  {
    List<MoodleUser> result = null;

    LinkedHashMap<String, String> criterias = new LinkedHashMap<>();
    criterias.put("criteria[0][key]", "auth");
    criterias.put("criteria[0][value]", "ldap");
//    criterias.put("criteria[0][key]", "email");
//    criterias.put("criteria[0][value]", "%");

    
    ResponseEntity<MoodleUsersResponse> response
      = restTemplate.getForEntity(
        uriBuilder("core_user_get_users", criterias),
        MoodleUsersResponse.class);

    if (response != null && response.getBody() != null
      && response.getStatusCode() == HttpStatus.OK)
    {
      result = response.getBody().getUsers();
    }

    return result;
  }

  // TODO: ...
  public List<MoodleRole> roles()
  {
    return new ArrayList<>();
  }

  /**
   * create user in Moodle 
   * @param user object
   * @return ???
   */
  public UsersCreateResponse usersCreateOld(MoodleUser user) throws Exception
  {
    LinkedHashMap<String, String> criteria = new LinkedHashMap<>();
    UsersCreateResponse result = null;
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    List<MoodleUser> list = new ArrayList<>();
    list.add( user );
    UsersCreateRequest payload = new UsersCreateRequest(list);
    
    HttpEntity<UsersCreateRequest> request = new HttpEntity<>(payload, headers);

    ResponseEntity<String> response1
      = restTemplate.postForEntity(
        uriBuilder("core_user_create_users", criteria), request,
        String.class);   
    LOGGER.debug( response1.toString() );
/*    
    ResponseEntity<UsersCreateResponse> response
      = restTemplate.postForEntity(
        uriBuilder("core_user_create_users", criteria), request,
        UsersCreateResponse.class);
    if (response != null && response.getBody() != null
      && response.getStatusCode() == HttpStatus.OK)
    {
      result = response.getBody();
    }

    if (result != null && result.list().isEmpty())
    {
      throw new Exception("user " + user.getEmail() + " could not be inserted");
    }
*/
    return result;
  }
  
    
  
  
  public boolean usersCreate(MoodleUser user) throws Exception
  {

    List<MoodleUser> list = new ArrayList<>();
    list.add( user );
    UsersCreateRequest payload = new UsersCreateRequest(list);    
    
    WebClient webClient = WebClient.create(); 
    Mono<ResponseEntity<String>> response = webClient.post()
      .uri(uriBuilder -> 
          uriBuilder.path(config.getMoodleBaseUrl() + "/webservice/rest/server.php")
            .queryParam("wstoken", wstoken)
            .queryParam("moodlewsrestformat", "json")
            .queryParam("wsfunction", "core_user_create_users")
            .build()
      )
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)      
      .body(Mono.just(payload), UsersCreateRequest.class)      
      .retrieve()
      .toEntity(String.class);      
      
//      .bodyToMono(String.class);
//    ResponseEntity<String> block = response.block();
//    LOGGER.debug( block.toString());
    LOGGER.debug( response.toString());

/*    
    ResponseEntity<UsersCreateResponse> response
      = restTemplate.postForEntity(
        uriBuilder("core_user_create_users", criteria), request,
        UsersCreateResponse.class);
    if (response != null && response.getBody() != null
      && response.getStatusCode() == HttpStatus.OK)
    {
      result = response.getBody();
    }

    if (result != null && result.list().isEmpty())
    {
      throw new Exception("user " + user.getEmail() + " could not be inserted");
    }
*/
    return true;
  }  
  

  // TODO: ...
  public MoodleUser usersUpdate(int id, MoodleUser user)
  {
    return user;
  }

  // TODO: ...
  public MoodleUser usersAnonymize(int id, MoodleAnonymousUser user)
  {
    return null;
  }
}
