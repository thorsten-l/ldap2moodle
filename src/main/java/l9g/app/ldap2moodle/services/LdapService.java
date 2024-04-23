package l9g.app.ldap2moodle.services;

import com.unboundid.asn1.ASN1GeneralizedTime;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.HashMap;
import javax.net.ssl.SSLSocketFactory;
import l9g.app.ldap2moodle.Config;
import l9g.app.ldap2moodle.handler.CryptoHandler;
import l9g.app.ldap2moodle.handler.LdapHandler;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author K.Borm
 */
@Service
public class LdapService
{
  private final static Logger LOGGER
    = LoggerFactory.getLogger(LdapHandler.class);  
  
  @Autowired
  private Config config;  
  
  @Autowired
  private CryptoHandler cryptoHandler;  
    

  private LDAPConnection getConnection() throws Exception
  {
    LOGGER.debug("host = " + config.getLdapHostname());
    LOGGER.debug("port = " + config.getLdapPort());
    LOGGER.debug("ssl = " + config.isLdapSslEnabled());
    LOGGER.debug("bind dn = " + config.getLdapBindDn());
    LOGGER.trace("bind pw = " + cryptoHandler.decrypt(config.
      getLdapBindPassword()));

    LDAPConnection ldapConnection;

    LDAPConnectionOptions options = new LDAPConnectionOptions();
    if (config.isLdapSslEnabled())
    {
      ldapConnection = new LDAPConnection(createSSLSocketFactory(), options,
        config.getLdapHostname(), config.getLdapPort(),
        config.getLdapBindDn(),
        cryptoHandler.decrypt(config.getLdapBindPassword()));
    }
    else
    {
      ldapConnection = new LDAPConnection(options,
        config.getLdapHostname(), config.getLdapPort(),
        config.getLdapBindDn(),
        cryptoHandler.decrypt(config.getLdapBindPassword()));
    }
    ldapConnection.setConnectionName(config.getLdapHostname());
    return ldapConnection;
  }

  
  private SSLSocketFactory createSSLSocketFactory() throws
    GeneralSecurityException
  {
    SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
    return sslUtil.createSSLSocketFactory();
  }  
  
  
  public void readLdapEntries(
    String filter, // search filter
    String baseDn,
    SearchScope scope,
    String[] attributes, // attributes to be delivered
    String identifier, // normally "cn"
    ASN1GeneralizedTime lastSyncTimestamp
    )
    throws Throwable
  {
    ldapEntryMap.clear();

    LOGGER.debug("filter={}", filter);

    try (LDAPConnection connection = getConnection())
    {
      SearchRequest searchRequest = new SearchRequest(
        baseDn, scope, filter, attributes);

      int totalSourceEntries = 0;
      ASN1OctetString resumeCookie = null;
      SimplePagedResultsControl responseControl = null;

      // int pagedResultSize = ldapConfig.getPagedResultSize() > 0
      //   ? ldapConfig.getPagedResultSize() : 1000;
      int pagedResultSize = 1000;

      do
      {
        searchRequest.setControls(
          new SimplePagedResultsControl(pagedResultSize, resumeCookie));

        SearchResult sourceSearchResult = connection.search(searchRequest);

        int sourceEntries = sourceSearchResult.getEntryCount();
        totalSourceEntries += sourceEntries;

        for (Entry entry : sourceSearchResult.getSearchEntries())
        {
          
          if (entry.getAttributeValue(identifier) == null)
          {
            LOGGER.error("attribute " + identifier + " is missing in ldap entry");
          }
          else
          {
            ldapEntryMap.put(entry.getAttributeValue(identifier).trim().toLowerCase(), entry);
          }

          responseControl = SimplePagedResultsControl.get(sourceSearchResult);

          if (responseControl != null)
          {
            resumeCookie = responseControl.getCookie();
          }
        }
      }
      while (responseControl != null && responseControl.moreResultsToReturn());

      if (totalSourceEntries == 0)
      {
        LOGGER.info("No entries to synchronize found");
      }
      else
      {
        LOGGER.
          info("build list from source DNs, {} entries", totalSourceEntries);
      }
    }
  }  
  
  @Getter
  private final HashMap<String, Entry> ldapEntryMap = new HashMap<>();
  
}
