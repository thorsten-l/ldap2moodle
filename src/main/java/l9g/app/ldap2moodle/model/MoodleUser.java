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
package l9g.app.ldap2moodle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@ToString
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MoodleUser
{/*
  public HashMap<String, String> toMap() {
    HashMap<String, String> userMap = new HashMap<String, String>();

    userMap.put("username", this.username);
    userMap.put("firstname", this.firstname);
    userMap.put("lastname", this.lastname);
    userMap.put("email", this.email);

    userMap.put("idnumber", this.idnumber);
    userMap.put("department", this.department);
    userMap.put("auth", this.auth);

    return userMap;
}*/


  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Integer id;

  private String username;

  private String firstname;

  private String lastname;

  private String fullname;

  private String email;
  
  private String idnumber;

  private String department;

  private int firstaccess;

  private int lastaccess;

  private String auth;

  private boolean suspended = false;

  private boolean confirmed;

  private String lang;

  private String theme;

  private String timezone;

  private int mailformat;

  private String description;

  private int descriptionformat;

  private String country;

  private String profileimageurlsmall;

  private String profileimageurl;

  private Map<String, String> customfields = new HashMap<>();
  
  public void addCustomField( String name, String value )
  {
    customfields.put( name, value );
  }
  
}
