package com.nordea.assignment.service;

import com.nordea.assignment.AssignmentApplication;
import com.nordea.assignment.client.RemoteServiceClient;
import com.nordea.assignment.dto.CountryListResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = AssignmentApplication.class)
@EnableAutoConfiguration
public class ResilienceTest {
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private RemoteServiceClient remoteClient;
    @Autowired
    private CountryService countryService;
    @MockBean
    private RestTemplate restTemplate;

    final String TEST_URL = "https://countries-remote-url.dummy";
    final String COUNTRY_1_NAME = "Country-1";
    final String RETRY_RESPONSE_MSG = "Response after retry";

    @Test
    public void doesNotCallRemoteServiceWhenCircuitBreakerIsOpen() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(remoteClient.circuitBreakerName);
        circuitBreaker.transitionToOpenState();
        assertThrows(CallNotPermittedException.class, () -> countryService.retrieveCountry(COUNTRY_1_NAME));
        verify(restTemplate, times(0)).getForObject(anyString(), any());
    }

    @Test
    public void retryWhenThereIsAnUnexpectedException() {
        CountryListResponse retryResponse = new CountryListResponse();
        retryResponse.setMsg(RETRY_RESPONSE_MSG);
        Mockito.when(restTemplate.getForObject(TEST_URL, CountryListResponse.class))
                .thenThrow(new RuntimeException("Test Retry"))
                .thenReturn(retryResponse);
        CountryListResponse response = remoteClient.getForObject(TEST_URL, CountryListResponse.class);
        assertEquals(RETRY_RESPONSE_MSG, response.getMsg());
        verify(restTemplate, times(2)).getForObject(anyString(), any());
    }
}