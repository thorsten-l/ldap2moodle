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

import com.fasterxml.jackson.annotation.*;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.lang.reflect.Field;
import java.util.*;

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
{

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Integer id;

  private String username;

  private String firstname;

  private String lastname;

  private String fullname;

  private String email;
  
  private String idnumber;

  private String department;


  private String auth;

  private Boolean suspended;

  private Boolean confirmed;

  private String lang;

  private String theme;

  private String timezone;

  private Integer firstaccess;

  private Integer lastaccess;
  
  private Integer mailformat;

  private String description;

  private Integer descriptionformat;

  private String country;

  private String profileimageurlsmall;

  private String profileimageurl;

  List<CustomField> customfields = new LinkedList<>();
//  private Map<String, String> customfields = new HashMap<>();

  public void addCustomField( String shortname, String value )
  {
    CustomField field = new CustomField();
    field.setShortname( shortname );
    field.setValue( value );
    // Todo: check if shortname is already in list
    customfields.add( field );
  }


  /**
   * creates a new MoodleUsr object with all values set to null except for the differences
   * @param ldapUser
   * @return new MoodleUser object
   */
  public MoodleUser diff(MoodleUser ldapUser) throws IllegalAccessException
  {
    MoodleUser diffUser = new MoodleUser();
    boolean diffFound = false;

    Field[] fields = this.getClass().getDeclaredFields();
    for (Field field : fields) {
      // String name = field.getName();
//      if (!"customfields".equals(field.getName())) {
        // handle field that is no custom field:
        // make member public :-)
        // field.setAccessible(true);
        // get value
        if (field.get(ldapUser) != field.get(this)) {
          diffFound = true;
          field.set(diffUser, field.get(ldapUser));
          // make member private again
          // field.setAccessible(false);
        }
//      }
    }
    if (diffFound)
    {
      diffUser.setId(this.id);
    }
    return diffFound?diffUser:null;
  }
}
