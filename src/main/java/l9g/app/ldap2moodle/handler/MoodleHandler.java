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
package l9g.app.ldap2moodle.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.model.MoodleAnonymousUser;
import l9g.app.ldap2moodle.model.MoodleRole;
import l9g.app.ldap2moodle.model.MoodleUser;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import l9g.app.ldap2moodle.client.MoodleClient;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Component
public class MoodleHandler
{
  private final static Logger LOGGER 
    = LoggerFactory.getLogger(MoodleHandler.class);

  @Autowired
  private Config config;

  @Autowired
  private MoodleClient moodleClient;

  @Bean
  public MoodleHandler moodleHandlerBean()
  {
    LOGGER.debug("getMoodleHandler");
    return this;
  }

  public int getAdminGroupId()
  {
    LOGGER.debug("getAdminGroupId");
    int adminGroupId = -1;

    List<MoodleRole> roles = moodleClient.roles();

    for (MoodleRole role : roles)
    {
      LOGGER.debug(role.toString());
      if ("Admin".equals(role.getName()))
      {
        adminGroupId = role.getId();
        break;
      }
    }

    return adminGroupId;
  }

  public void readMoodleUsers()
  {
    LOGGER.debug("readMoodleUsers");
    moodleUsersList = moodleClient.users();
    moodleUsersMap.clear();
    moodleUsersList.forEach(user -> moodleUsersMap.put(user.getLogin(), user));
  }

  public MoodleUser createUser(MoodleUser user)
  {
    if (config.isDryRun())
    {
      LOGGER.debug("CREATE DRY RUN: " + user);
    }
    else
    {
      LOGGER.debug("CREATE: " + user);
      try
      {
        user = moodleClient.usersCreate(user);
      }
      catch (Throwable t)
      {
        LOGGER.error("*** CREATE FAILED *** " + t.getMessage());
      }
    }

    return user;
  }

  public MoodleUser updateUser(MoodleUser user)
  {
    if (config.isDryRun())
    {
      LOGGER.debug("UPDATE DRY RUN: " + user);
    }
    else
    {
      try
      {
        LOGGER.debug("UPDATE: " + objectMapper.writeValueAsString(user));
        user = moodleClient.usersUpdate(user.getId(), user);
      }
      catch (Throwable t)
      {
        LOGGER.error("*** UPDATE FAILED *** " + t.getMessage());
      }
    }

    return user;
  }

  public void deleteUser(MoodleUser user)
  {
    if (config.isDryRun())
    {
      LOGGER.debug("DELETE DRY RUN: " + user);
    }
    else
    {
      LOGGER.debug("DELETE: " + user);
      try
      {
        // moodleClient.usersDelete(user.getId());
        moodleClient.usersAnonymize(user.getId(),
          new MoodleAnonymousUser(user.getLogin()));
      }
      catch (Throwable t)
      {
        LOGGER.error("*** DELETE (Anonymize) FAILED *** " + t.getMessage());
      }
    }
  }

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private final Map<String, MoodleUser> moodleUsersMap = new HashMap<>();

  @Getter
  private List<MoodleUser> moodleUsersList;
}
