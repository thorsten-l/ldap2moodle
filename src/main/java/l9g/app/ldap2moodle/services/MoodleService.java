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

  record UsersCreateResponse( List<UserCreateResponse> list) {}

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

    UriComponents uriComponents = builder.build().encode();
    LOGGER.debug( "uri={}", uriComponents.toUriString() );

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

  public WebClient webClient()
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

  // from https://github.com/bantonia/MoodleRest/blob/master/src/net/beaconhillcott/moodlerest/MoodleRestUser.java
  private String getCreateUri(MoodleUser[] users) throws MoodleRestException // Argh!
  {
    // try {
      StringBuilder data=new StringBuilder();
      for (int i=0;i<users.length;i++) {
        if (users[i]==null) throw new MoodleRestException(MoodleRestException.USER_NULL);
        if (users[i].getUsername()==null) throw new MoodleRestException(MoodleRestException.USERNAME_NULL); else data.append("&").append("users["+i+"][username]").append("=").append(users[i].getUsername());
        if (users[i].getFirstname()==null) throw new MoodleRestException(MoodleRestException.FIRSTNAME_NULL); else data.append("&").append("users["+i+"][firstname]").append("=").append(users[i].getFirstname());
        if (users[i].getLastname()==null) throw new MoodleRestException(MoodleRestException.LASTNAME_NULL); else data.append("&").append("users["+i+"][lastname]").append("=").append(users[i].getLastname());
        if (users[i].getEmail()==null) throw new MoodleRestException(MoodleRestException.EMAIL_NULL); else data.append("&").append("users["+i+"][email]").append("=").append(users[i].getEmail());
        if (users[i].getAuth()!=null) data.append("&").append("users["+i+"][auth]").append("=").append(users[i].getAuth());
        if (users[i].getIdnumber()!=null) data.append("&").append("users["+i+"][idnumber]").append("=").append(users[i].getIdnumber());
        if (users[i].getLang()!=null) data.append("&").append("users["+i+"][lang]").append("=").append(users[i].getLang());
        if (users[i].getTimezone()!=null) data.append("&").append("users["+i+"][timezone]").append("=").append(users[i].getTimezone());
        if (users[i].getDescription()!=null) data.append("&").append("users["+i+"][description]").append("=").append(users[i].getDescription());
        if (users[i].getCountry()!=null) data.append("&").append("users["+i+"][country]").append("=").append(users[i].getCountry());
//        if (users[i].getAlternatename()!=null) data.append("&").append("users["+i+"][alternatename]").append("=").append(user[i].getAlternatename());
        if( users[ i ].getCustomfields() != null )
        {
          for( int j = 0; j < users[ i ].getCustomfields().size(); j++ )
          {
            data.append( "&" )
              .append( "users[" + i + "][customfields][" + j + "][type]" )
              .append( "=" )
              .append( users[ i ].getCustomfields().get( j ).getName() );
            data.append( "&" )
                .append( "users[" + i + "][customfields][" + j + "][value]" )
                .append( "=" )
                .append( users[ i ].getCustomfields().get( j ).getValue() );
          }
        }
      }
      data.trimToSize();
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

    return data.toString();
  }

  public boolean usersCreate( MoodleUser user )
    throws Exception
  {
    LOGGER.debug( "usersCreate" );
    MoodleUser[] moodleUsers = new MoodleUser[1];
    moodleUsers[0] = user;

    String query = this.getCreateUri(moodleUsers);
    Mono<String> response1 = this.webClient().post()
      .uri(uriBuilder
          -> uriBuilder.path("/webservice/rest/server.php")
          .queryParam("wstoken", wstoken)
          .queryParam("moodlewsrestformat", "json")
          .queryParam("wsfunction", "core_user_create_users")
          .query(query)
          .build())
      .accept( MediaType.APPLICATION_JSON )
      .exchangeToMono( response ->
      {
        LOGGER.info( response.toString() );
        if( response.statusCode().equals( HttpStatus.OK ) )
        {          
          LOGGER.info( "OK" );
          return response.bodyToMono( String.class );
        }
        else
        {
          // Turn to error
          LOGGER.info( "not ok" );
          return response.createException().flatMap(Mono::error);
          // return response.createError();
        }
      } );

    String body = response1.block();

    LOGGER.info( body );

    // parse json, could contain error message or response in case of no error
    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();
    try
    {
      UsersCreateResponse usersResponse = objectMapper.readValue(body, UsersCreateResponse.class);
      LOGGER.info("User has been inserted successfully");
      LOGGER.info(usersResponse.toString());
    }
    catch(Exception e)
    {
      LOGGER.info("User has NOT been inserted successfully");
      MoodleError errorResponse = objectMapper.readValue(body, MoodleError.class);
      LOGGER.info( errorResponse.message);
      return false;
    }

    return true;
  }

  // TODO: ...
  public MoodleUser usersUpdate( int id, MoodleUser user )
  {
    return user;
  }

  // TODO: ...
  public MoodleUser usersAnonymize( int id, MoodleAnonymousUser user )
  {
    return null;
  }

}
