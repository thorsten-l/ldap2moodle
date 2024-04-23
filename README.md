# ldap2moodle

Create, update and delete moodle user entries from a selected LDAP directory server.

## Preconditions

* Users to be synchronized are authenticated with a single plugin (e.g. ldap or oicd)
* Moodle admin users have bee created with a different authentication method (normally 'manual').
  Therefore they do not need to be considered separately here.
* Users who are not in Ldap will be suspended.

## Installation

* enable moodle webservice for REST protocol

https://docs.moodle.org/403/en/Using_web_services

The following webservice commands need to be enabled: 

* core_user_get_users
* core_user_create_users
* core_user_update_users

## Configuration



## LDAP to Moodle attribute mapping

The mapping will be done within a JavaScript function.
This `function` will be executed for every entry in the LDAP resultset.
 
```javascript
(
  function ldap2moodle( mode, moodleUser, ldapEntry )
  {
    // mode : "test", "create, "update"
    
    moodleUser.setFirstname(ldapEntry.getAttributeValue("givenname"));
    moodleUser.setLastname(ldapEntry.getAttributeValue("sn"));
    moodleUser.setEmail(ldapEntry.getAttributeValue("mail"));
    moodleUser.setPhone(ldapEntry.getAttributeValue("telephoneNumber"));
    moodleUser.setFax(ldapEntry.getAttributeValue("facsimileTelephoneNumber"));
    moodleUser.setWeb("https://www.myorg.de");
    moodleUser.setOrganization("MyOrg");
    moodleUser.setVerified(true);

    if ( "create" === mode || "test" === mode )
    {
      if ( ldapEntry.getAttributeValue("institute") === "CC" )
      {
        moodleUser.setDepartment( "CC" );
        moodleUser.setRoles( [ "Agent", "Customer" ] );
      }
      else
      {
        moodleUser.setRoles( [ "Customer" ] ); // array of String
      }
    }
  }
);
```

