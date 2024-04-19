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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.logging.LogLevel;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.handler.CryptoHandler;
import l9g.app.ldap2moodle.model.MoodleAnonymousUser;
import l9g.app.ldap2moodle.model.MoodleRole;
import l9g.app.ldap2moodle.model.MoodleUser;
import l9g.app.ldap2moodle.model.MoodleUsersResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
public class MoodleService
{
  
  public class MoodleRestException extends Exception {

    public static final String AUTH_NULL="No credential provided";
    public static final String USERNAME_NULL="Username cannot be null";
    public static final String FIRSTNAME_NULL="Firstname cannot be null";
    public static final String LASTNAME_NULL="Lastname cannot be null";
    public static final String EMAIL_NULL="Email cannot be null";
    public static final String USER_NULL="User cannot be null";
    public static final String INVALID_USERID="Bad user id";

    MoodleRestException() {}

    MoodleRestException(String msg) {
      super(msg);
    }

    @Override
    public String getMessage() {
      return super.getMessage();
    }
  }

  private final static Logger LOGGER =
    LoggerFactory.getLogger( MoodleService.class );

  final private RestTemplate restTemplate = new RestTemplate();

  final private String wstoken;

  final private Config config;

  @Autowired
  public MoodleService( Config config, CryptoHandler cryptoHandler )
  {
    LOGGER.debug( "MoodleService constructor" );

    this.wstoken = cryptoHandler.decrypt( config.getMoodleToken() );
    this.config = config;
  }

  record UserCreateResponse( int id, String username) {}
  
  // TODO: figure out correct type
  // record UserUpdateResponse( String warnings) {} // TODO: item, itemid, warningcode message ????
  record MoodleError( String exception, String errorcode, String message, String debuginfo) {}
  
  

  /**
   * create URI with parameters
   *
   * @param wsfunction
   * @param parameters
   *
   * @return Uri object
   */
  private URI uriBuilder( String wsfunction,
    LinkedHashMap<String, String> parameters )
  {
    UriComponentsBuilder builder = UriComponentsBuilder
      .fromHttpUrl( config.getMoodleBaseUrl() + "/webservice/rest/server.php" )
      .queryParam( "wstoken", wstoken )
      .queryParam( "moodlewsrestformat", "json" )
      .queryParam( "wsfunction", wsfunction );

    if( parameters != null && !parameters.isEmpty() )
    {
      parameters.forEach( ( key, value ) -> builder.queryParam( key, value ) );
    }

    UriComponents uriComponents = builder.build();
    LOGGER.debug( "uri={}", uriComponents.toUriString() );
    uriComponents = uriComponents.encode();
    LOGGER.debug( "uri={}", uriComponents.toUriString() );

    return uriComponents.toUri();
  }
  
  private WebClient webClient()
  {
    var httpClient = HttpClient
      .create()
      .wiretap( "reactor.netty.http.client.HttpClient",
        LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL );

    return WebClient
      .builder()
      .clientConnector( new ReactorClientHttpConnector( httpClient ) )
      .baseUrl( config.getMoodleBaseUrl() )
      .build();
  }  
  
  /**
   * send post request with given function and given users to moodle
   * 
   * @param function
   * @param moodleUsers
   * @return
   * @throws l9g.app.ldap2moodle.services.MoodleService.MoodleRestException 
   */
  private String postToMoodle( String function, MoodleUser[] moodleUsers )
    throws MoodleRestException
  {
    return this.webClient()
      .post()
      .uri( this.uriBuilder( function, this.getUserParams(moodleUsers) ))
      .accept( MediaType.APPLICATION_JSON )
      .exchangeToMono( response ->
      {
        LOGGER.info( response.toString() );
        if( response.statusCode().equals( HttpStatus.OK ) )
        {
          return response.bodyToMono( String.class );
        }
        else
        {
          return response.createException().flatMap(Mono::error);
        }
      } )
      .block();
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
    criterias.put( "criteria[0][key]", "auth" );
    criterias.put( "criteria[0][value]", "ldap" );
//    criterias.put("criteria[0][key]", "email");
//    criterias.put("criteria[0][value]", "%");

    ResponseEntity<MoodleUsersResponse> response =
      restTemplate.getForEntity(
        uriBuilder( "core_user_get_users", criterias ),
        MoodleUsersResponse.class );

    if( response != null && response.getBody() != null
      && response.getStatusCode() == HttpStatus.OK )
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
   * create uri parameters for special moodle 'Rest' URI 
   * from user array
   * 
   * @param users
   * @return
   * @throws l9g.app.ldap2moodle.services.MoodleService.MoodleRestException 
   */
  private LinkedHashMap<String, String> getUserParams(MoodleUser[] users) throws MoodleRestException 
  {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
   
    for (int i=0;i<users.length;i++) {
      if (users[i] == null) 
        throw new MoodleRestException(MoodleRestException.USER_NULL);
      if( users[ i ].getUsername() == null )
      {
        throw new MoodleRestException( MoodleRestException.USERNAME_NULL );
      }
      // mandatory fields
      data.put( "users[" + i + "][username]",  users[ i ].getUsername());
      data.put( "users[" + i + "][firstname]",  users[ i ].getFirstname());
      data.put( "users[" + i + "][lastname]",  users[ i ].getLastname());
      data.put( "users[" + i + "][email]",  users[ i ].getEmail());

      if( users[ i ].getId() != null )
      {
        data.put( "users[" + i + "][id]",  users[ i ].getId().toString());
      }      
      if( users[ i ].getAuth() != null )
      {
        data.put( "users[" + i + "][auth]",  users[ i ].getAuth());
      }
      if( users[ i ].getIdnumber() != null )
      {
        data.put( "users[" + i + "][idnumber]",  users[ i ].getIdnumber());
      }
      if( users[ i ].getLang() != null )
      {
        data.put( "users[" + i + "][lang]",  users[ i ].getLang());
      }
      if( users[ i ].getTimezone() != null )
      {
        data.put( "users[" + i + "][timezone]",  users[ i ].getTimezone());
      }
      if( users[ i ].getDescription() != null )
      {
        data.put( "users[" + i + "][description]",  users[ i ].getDescription());
      }
      if( users[ i ].getDepartment()!= null )
      {
        data.put( "users[" + i + "][department]",  users[ i ].getDepartment());
      }      
      if( users[ i ].getCountry() != null )
      {
        data.put( "users[" + i + "][country]",  users[ i ].getCountry());
      }
      
      // a bit strange ... but in order to use foreach XD
      AtomicInteger index = new AtomicInteger(0);
      AtomicInteger userIndex = new AtomicInteger(i);
      users[ i ].getCustomfields().forEach((key, value) -> 
      {
        int j = index.getAndIncrement();
        data.put( "users[" + userIndex.get() + "][customfields][" + j + "][type]", key );
        data.put( "users[" + userIndex.get() + "][customfields][" + j + "][value]", value );
      });
    }
/*
      NodeList elements=MoodleCallRestWebService.call(data.toString());
      for (int j=0;j<elements.getLength();j+=2) {
        hash.put(elements.item(j+1).getTextContent(), elements.item(j).getTextContent());
      }
    }  catch (UnsupportedEncodingException ex) {
      Logger.getLogger(MoodleRestUser.class.getName()).log(Level.SEVERE, null, ex);
    }

 */
    /*
    for (int i=0; i<user.length; i++) {
      user[i].setId(Long.parseLong((String)(hash.get(user[i].getUsername()))));
    }*/

    return data;
  }

  /**
   * create user(s?)
   * @param user
   * @return
   * @throws Exception 
   */
  public MoodleUser usersCreate( MoodleUser user )
    throws Exception
  {
    LOGGER.debug( "usersCreate" );

    MoodleUser[] moodleUsers = new MoodleUser[1];
    moodleUsers[0] = user;
   
    final String body = postToMoodle("core_user_create_users", moodleUsers);

    LOGGER.info( body );

    // parse json, could contain error message or response in case of no error
    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();
    try
    {
      LOGGER.info("User has been inserted successfully");
      UserCreateResponse[] usersResponse = objectMapper.readValue(body, UserCreateResponse[].class);
      LOGGER.info(String.valueOf(usersResponse[0].id()));
      user.setId( usersResponse[0].id() );
      return user;
    }
    catch(JsonProcessingException e)
    {
      LOGGER.info("User has NOT been inserted successfully");
      MoodleError errorResponse = objectMapper.readValue(body, MoodleError.class);
      LOGGER.error( errorResponse.message);
      throw new MoodleRestException(errorResponse.message);
    }

  }

  /**
   * update user(s?)
   * @param user
   * @return
   * @throws Exception 
   */
  public MoodleUser usersUpdate( MoodleUser user )
    throws Exception    
  {
    MoodleUser[] moodleUsers = new MoodleUser[1];
    moodleUsers[0] = user;
   
    final String body = postToMoodle("core_user_update_users", moodleUsers);

    LOGGER.info( body );

    // parse json, could contain error message or response in case of no error
    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();
    try
    {
      LOGGER.info("User has been updated successfully");
//      String response = objectMapper.readValue(body, String.class);
      
//      UserUpdateResponse[] usersResponse = objectMapper.readValue(body, UserUpdateResponse[].class);
//      LOGGER.info( response );
      return user;
    }
    catch(Exception e)
    {
      LOGGER.info("User has NOT been updated successfully");
      MoodleError errorResponse = objectMapper.readValue(body, MoodleError.class);
      LOGGER.error( errorResponse.message);
      throw new MoodleRestException(errorResponse.message);
    }
    
  }

  // TODO: ...
  public MoodleUser usersAnonymize( int id, MoodleAnonymousUser user )
    throws Exception    
  {
    return null;
  }

}
