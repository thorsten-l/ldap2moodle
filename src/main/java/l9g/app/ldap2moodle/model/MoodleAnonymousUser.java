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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@ToString
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MoodleAnonymousUser
{
  public MoodleAnonymousUser(String login)
  {
    this.login = this.firstname = this.lastname = login;
    this.email = login + "@anonymous.net";
    this.department = this.fax = this.mobile
      = this.note = this.phone = this.web = "";
  }

  private Integer organization_id;

  private final String login;

  private final String firstname;

  private final String lastname;

  private final String email;

  private Object image;

  private Object image_source;

  private final String web;

  private final String phone;

  private final String fax;

  private final String mobile;

  private final String department;

  private boolean vip;

  private boolean verified;

  private boolean active;

  private final String note;

  private String source;

  private boolean out_of_office;

  private Date out_of_office_start_at;

  private Date out_of_office_end_at;

  private Integer out_of_office_replacement_id;

  private MoodlePreferences preferences;

  private List<String> roles;

  private List<Integer> role_ids;

  private List<Integer> organization_ids;

  private List<Integer> authorization_ids;

  private List<Integer> overview_sorting_ids;

  private Map<String, String[]> group_ids;
}
