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
import com.unboundid.asn1.ASN1GeneralizedTime;
import com.unboundid.ldap.sdk.Entry;
import java.util.Objects;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.LogbackConfig;
import l9g.app.ldap2moodle.TimestampUtil;
import l9g.app.ldap2moodle.engine.JavaScriptEngine;
import l9g.app.ldap2moodle.handler.LdapHandler;
import l9g.app.ldap2moodle.model.MoodleUser;
import l9g.app.ldap2moodle.handler.MoodleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Command(group = "Application")
public class ApplicationCommands
{
  private final static Logger LOGGER
    = LoggerFactory.getLogger(ApplicationCommands.class);

  @Autowired
  private Config config;

  @Autowired
  private LdapHandler ldapHandler;

  @Autowired
  private MoodleHandler moodleHandler;

  @Autowired
  private LogbackConfig logbackConfig;

  @Command(description = "sync users from LDAP to Moodle")
  public void sync(
    @Option(longNames = "full-sync", defaultValue = "false") boolean fullSync,
    @Option(longNames = "dry-run", defaultValue = "false") boolean dryRun,
    @Option(longNames = "debug", defaultValue = "false") boolean debug,
    @Option(longNames = "trace", defaultValue = "false") boolean trace
  ) throws Throwable
  {
    logbackConfig.getRootLogger().setLevel(Level.INFO);
    logbackConfig.getL9gLogger().setLevel(Level.INFO);

    if (debug)
    {
      logbackConfig.getL9gLogger().setLevel(Level.DEBUG);
    }

    if (trace)
    {
      debug = true;
      logbackConfig.getRootLogger().setLevel(Level.TRACE);
      logbackConfig.getL9gLogger().setLevel(Level.TRACE);
    }

    LOGGER.info("dryRun = '{}', debug = '{}', trace = '{}'",
      dryRun, debug, trace);
    config.setDebug(debug);
    config.setDryRun(dryRun);

    LOGGER.debug("Los gehts!");
    TimestampUtil timestampUtil = new TimestampUtil("moodle-users");

    moodleHandler.readMoodleUsers();

//    Integer adminGroupId = moodleHandler.getAdminGroupId();
//    LOGGER.debug("adminGroupId=" + adminGroupId);

    ///////////////////////////////////////////////////////////////////////////
    // DELETE
    
    // iterate through all Moodle users and check if users exists in 
    // LDAP users. If false then suspend user in Moodle
    
    LOGGER.info( "*** DELETE USERS");
    
    // read users from Ldap without attributes (just uids)
    ldapHandler.readAllLdapEntryUIDs();
    moodleHandler.getMoodleUsersMap().forEach( ( key, user ) ->
    {
      if( !ldapHandler.getLdapEntryMap().containsKey( user.getUsername() ) )
      {
        // moodle user does not exist in ldap user list
        // => suspend in moodle        
        moodleHandler.suspendUser( user );
      }
    } );

    ///////////////////////////////////////////////////////////////////////////
    // CREATE / UPDATE
    
    // iterate through all LDAP users:
    // if user does not exist in Moodle => create 
    // if user exists in Moodle => check for update
    
    ASN1GeneralizedTime timestamp;
    if (fullSync)
    {
      timestamp = new ASN1GeneralizedTime(0l); // 01.01.1970, unix time 0
    }
    else
    {
      timestamp = timestampUtil.getLastSyncTimestamp();
    }

    // read users from LDAP with attributes
    ldapHandler.readLdapEntries(timestamp, true);

    LOGGER.info( "*** UPDATE/CREATE USERS");

    try (JavaScriptEngine js = new JavaScriptEngine())
    {
      final int noEntries = ldapHandler.getLdapEntryMap().size();
      int entryCounter = 0;

      for (Entry entry : ldapHandler.getLdapEntryMap().values())
      {
        entryCounter++;
        LOGGER.debug("{}/{}", entryCounter, noEntries);
        String login = entry.getAttributeValue(config.getLdapUserId());
        MoodleUser moodleUser = moodleHandler.getMoodleUsersMap().get(login);

        if (moodleUser != null)
        {
          // UPDATE
          MoodleUser ldapUser = new MoodleUser();
          ldapUser.setId(moodleUser.getId());
          ldapUser.setUsername(login);

          js.getValue().executeVoid("update", ldapUser, entry);
          MoodleUser diffUser = moodleUser.diff(ldapUser);
          moodleHandler.updateUser(diffUser);
        }
        else
        {
          // CREATE
          moodleUser = new MoodleUser();
          moodleUser.setUsername(login);
          js.getValue().executeVoid("create", moodleUser, entry);
          moodleHandler.createUser(moodleUser);
        }
      }
    }
    ///////////////////////////////////////////////////////////////////////////
    if (!dryRun)
    {
      timestampUtil.writeCurrentTimestamp();
    }

    logbackConfig.getRootLogger().setLevel(Level.INFO);
    logbackConfig.getL9gLogger().setLevel(Level.INFO);
  }
}
