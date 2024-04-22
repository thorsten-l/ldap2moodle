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
import java.lang.reflect.Field;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.handler.CryptoHandler;
import l9g.app.ldap2moodle.model.MoodleAnonymousUser;
import l9g.app.ldap2moodle.model.MoodleRole;
import l9g.app.ldap2moodle.model.MoodleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    public static final String USERNAME_NULL="Username cannot be null";
    public static final String USER_NULL="User cannot be null";

    MoodleRestException(String msg) {
      super(msg);
    }

    @Override
    public String getMessage() {
      return super.getMessage();
    }
  }
  
  // Response classes
  record UserCreateResponse( int id, String username) {}
  record GetUsersResponse( List<MoodleUser> users, List<String> warnings) {}  
  // record UserUpdateResponse( String warnings) {} // TODO: item, itemid, warningcode message ????
  
  record MoodleErrorResponse( String exception, String errorcode, String message, String debuginfo) {}

  

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
   * send post request with given function and given fetchUsers to moodle
   * 
   * @param function
   * @param moodleUsers
   * @return
   * @throws l9g.app.ldap2moodle.services.MoodleService.MoodleRestException 
   */
  private String postToMoodle( String function, MoodleUser[] moodleUsers )
    throws MoodleRestException, IllegalAccessException
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
  
  
  private String getFromMoodle( String function, LinkedHashMap<String, String> params )
  {     
    return this.webClient()      
      .get()
        .uri( this.uriBuilder( function, params ))
        .accept( MediaType.APPLICATION_JSON )
      .exchangeToMono( response -> // use flux here for handling large response ???
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
  public List<MoodleUser> fetchUsers() throws Exception
  {
    List<MoodleUser> result = null;

    LinkedHashMap<String, String> criteria = new LinkedHashMap<>();
    /*    criteria.put( "criteria[0][key]", "email" );
    criteria.put( "criteria[0][value]", "%" );*/

    criteria.put( "criteria[0][key]", "auth" );
    criteria.put( "criteria[0][value]", "ldap" );

    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();   
           
    final String body = this.getFromMoodle( "core_user_get_users", criteria );      
    try
    {
      GetUsersResponse usersResponse = objectMapper.readValue(body, GetUsersResponse.class);
          
      result = usersResponse.users();
      if (!usersResponse.warnings().isEmpty())
      {
        LOGGER.warn( "warning avaiable for users (on core_user_get_users)" );        
      }
    }
    catch(JsonProcessingException e)
    {
      // conversion failed => cast string response to error class
      LOGGER.info("Users have NOT been fetched");
      MoodleErrorResponse errorResponse = objectMapper.readValue(body, MoodleErrorResponse.class);
      LOGGER.error( errorResponse.message);
      throw new MoodleRestException(errorResponse.message);
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
  private LinkedHashMap<String, String> getUserParams(MoodleUser[] users) throws MoodleRestException, IllegalAccessException 
  {
    LinkedHashMap<String, String> data = new LinkedHashMap<>();
   
    for( int i = 0; i < users.length; i++ )
    {
      MoodleUser user = users[ i ];
      if( user == null )
      {
        throw new MoodleRestException( MoodleRestException.USER_NULL );
      }
      if( user.getUsername() == null )
      {
        throw new MoodleRestException( MoodleRestException.USERNAME_NULL );
      }

      // get fields via Reflection API
      Field[] fields = user.getClass().getDeclaredFields();
      for( Field field : fields )
      {
        // String name = field.getName();
        if( !"customfields".equals( field.getName() ) )
        {
          // make member public :-)
          field.setAccessible( true );
          // get value
          if( field.get( user ) != null )
          {
            // value is set => put into hash map
            if (field.getType() == Boolean.class)
            {
              // convert boolean true/false to 1/0
              data.put( "users[" + i + "][" + field.getName() + "]", field.get( user ).equals( Boolean.TRUE)?"1":"0" );              
            } else {
              data.put( "users[" + i + "][" + field.getName() + "]", field.get( user ).toString() );
            }              
          }
          // make member private again
          field.setAccessible( false );
        }
      }

      // a bit strange ... but in order to use foreach loop XD
      AtomicInteger index = new AtomicInteger( 0 );
      AtomicInteger userIndex = new AtomicInteger( i );
      user.getCustomfields().forEach( ( key, value ) ->
      {
        int j = index.getAndIncrement();
        data.put( "users[" + userIndex.get() + "][customfields][" + j + "][type]", key );
        data.put( "users[" + userIndex.get() + "][customfields][" + j + "][value]", value );
      } );
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
  public MoodleUser createUsers( MoodleUser user )
    throws Exception
  {
    LOGGER.debug( "usersCreate" );

    MoodleUser[] moodleUsers = new MoodleUser[1];
    moodleUsers[0] = user;
   
    final String body = postToMoodle("core_user_create_users", moodleUsers);

    LOGGER.info( body );

    // parse json, could contain error message or response in case of no error
    ObjectMapper objectMapper = new ObjectMapper();
    try
    {
      // try and cast string to response class expected
      UserCreateResponse[] usersResponse = objectMapper.readValue(body, UserCreateResponse[].class);
      // conversion succeeded => handle response
      LOGGER.info("User has been inserted successfully");
      LOGGER.info(String.valueOf(usersResponse[0].id()));
      user.setId( usersResponse[0].id() );
      return user;
    }
    catch(JsonProcessingException e)
    {
      // conversion failed =>
      // cast string response to error class
      LOGGER.info("User has NOT been inserted successfully");
      MoodleErrorResponse errorResponse = objectMapper.readValue(body, MoodleErrorResponse.class);
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
  public MoodleUser updateUsers( MoodleUser user )
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
      MoodleErrorResponse errorResponse = objectMapper.readValue(body, MoodleErrorResponse.class);
      LOGGER.error( errorResponse.message);
      throw new MoodleRestException(errorResponse.message);
    }
    
  }

  // TODO: ...
  public MoodleUser anonymizeUsers( int id, MoodleAnonymousUser user )
    throws Exception    
  {
    return null;
  }

}
