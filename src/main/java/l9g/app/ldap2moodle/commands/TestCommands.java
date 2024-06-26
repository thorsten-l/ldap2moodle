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
package l9g.app.ldap2moodle.commands;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.asn1.ASN1GeneralizedTime;
import com.unboundid.ldap.sdk.Entry;
import l9g.app.ldap2moodle.LogbackConfig;
import l9g.app.ldap2moodle.services.MoodleService;
import l9g.app.ldap2moodle.engine.JavaScriptEngine;
import l9g.app.ldap2moodle.handler.LdapHandler;
import l9g.app.ldap2moodle.handler.MoodleHandler;
import l9g.app.ldap2moodle.model.MoodleAnonymousUser;
import l9g.app.ldap2moodle.model.MoodleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Command(group = "Test")
public class TestCommands
{
  private final static Logger LOGGER
    = LoggerFactory.getLogger(TestCommands.class);

  @Autowired
  private LdapHandler ldapHandler;

  @Autowired
  private MoodleHandler moodleHandler;

  @Autowired
  private LogbackConfig logbackConfig;

  @Autowired
  private MoodleService moodleService;

  @Command(alias = "t1", description = "test javascipt file with ldap data")
  public void testJavaScript() throws Throwable
  {
    ldapHandler.readLdapEntries(new ASN1GeneralizedTime(0), true);
    ObjectMapper objectMapper = new ObjectMapper();

    try (JavaScriptEngine js = new JavaScriptEngine())
    {
      String[] loginList = ldapHandler.getLdapEntryMap().keySet().toArray(
        String[]::new);

      for (String login : loginList)
      {
        Entry entry = ldapHandler.getLdapEntryMap().get(login);
        System.out.println("\n" + entry);
        MoodleUser user = new MoodleUser();
        user.setUsername(login);
        js.getValue().executeVoid("test", user, entry);
        System.out.println(objectMapper.writeValueAsString(user));
      }
    }
  }

  @Command(alias = "t2", description = "show anonymous user")
  public void testAnonymousUser() throws Throwable
  {
    ObjectMapper objectMapper = new ObjectMapper();
    MoodleAnonymousUser user = new MoodleAnonymousUser("eid9519122");
    System.out.println("user=" + objectMapper.writeValueAsString(user));
  }

  @Command(alias = "t3", description = "send test error mail")
  public void testLoggerErrorMail() throws Throwable
  {
    logbackConfig.getRootLogger().setLevel(Level.INFO);
    logbackConfig.getL9gLogger().setLevel(Level.INFO);

    LOGGER.info("INFO");
    LOGGER.debug("DEBUG");
    LOGGER.trace("TRACE");
    LOGGER.warn("WARN");
    LOGGER.error("ERROR");

    //////////////////
    logbackConfig.getL9gLogger().setLevel(Level.DEBUG);

    //////////////////
    LOGGER.info("INFO");
    LOGGER.debug("DEBUG");
    LOGGER.trace("TRACE");
    LOGGER.warn("WARN");
    LOGGER.error("ERROR");

    logbackConfig.getL9gLogger().setLevel(Level.INFO);

    LOGGER.info("INFO");
    LOGGER.info(logbackConfig.getNotificationMarker(),
      "This is a test notification INFO mail.");
  }

  @Command(alias = "t4", description = "read all moodle users")
  public void testReadAllMoodleUsers() throws Throwable
  {
    logbackConfig.getL9gLogger().setLevel(Level.DEBUG);
    LOGGER.debug("testReadAllMoodleUsers");
    moodleHandler.readMoodleUsers();
    moodleHandler.getMoodleUsersList()
      .forEach(entry -> System.out.println(entry.toString()));
  }
}
