package Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HeaderContainer {

    @JsonProperty("SecurityContext")
    private String securityContext; // Also stringified

    @JsonProperty("CommandType")
    private String commandType;

    public String getSecurityContext() { return securityContext; }
    public void setSecurityContext(String securityContext) { this.securityContext = securityContext; }
}
