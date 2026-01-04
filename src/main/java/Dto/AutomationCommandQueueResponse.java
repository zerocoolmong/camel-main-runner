package Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AutomationCommandQueueResponse {
    @JsonProperty("Body")
    private String body; // Contains the stringified JSON

    @JsonProperty("Method")
    private String method;

    @JsonProperty("Headers")
    private HeaderContainer headers;

    @JsonProperty("TracingInformation")
    private String tracingInformation;

    // Standard Getters and Setters
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public HeaderContainer getHeaders() { return headers; }
    public void setHeaders(HeaderContainer headers) { this.headers = headers; }
}
