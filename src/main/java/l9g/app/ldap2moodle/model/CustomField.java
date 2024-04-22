package l9g.app.ldap2moodle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author k.borm
 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @Getter
  @Setter
  public class CustomField
  {
    private String shortname;
    private String value;
    // private String type;
    // private String displayvalue;
    // private String name;
  }
