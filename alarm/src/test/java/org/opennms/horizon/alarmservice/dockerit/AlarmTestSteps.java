/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 ******************************************************************************/

package org.opennms.horizon.alarmservice.dockerit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.opennms.horizon.alarmservice.model.AlarmDTO;
import org.opennms.horizon.alarmservice.model.AlarmSeverity;
import org.opennms.horizon.alarmservice.rest.AlarmCollectionDTO;
import org.opennms.horizon.events.proto.Event;

@Slf4j
public class AlarmTestSteps {

    public static final int DEFAULT_HTTP_SOCKET_TIMEOUT = 15_000;

    //
    // Test Configuration
    //
    private String applicationBaseUrl;
    private String kafkaRestBaseUrl;

    //
    // Test Runtime Data
    //
    private Response restAssuredResponse;
    private JsonPath parsedJsonResponse;
    private Long lastAlarmId;

//========================================
// Gherkin Rules
//========================================

    @Given("Application Base URL in system property {string}")
    public void applicationBaseURLInSystemProperty(String systemProperty) {
        this.applicationBaseUrl = System.getProperty(systemProperty);

        log.info("Using BASE URL {}", this.applicationBaseUrl);
    }

    @Given("Kafka Rest Server URL in system property {string}")
    public void kafkaRestServerURLInSystemProperty(String systemProperty) {
        this.kafkaRestBaseUrl = System.getProperty(systemProperty);

        log.info("Using KAFKA REST BASE URL {}", this.kafkaRestBaseUrl);
    }

    @Then("Send POST request to application at path {string}")
    public void sendPOSTRequestToApplicationAtPath(String path) throws Exception {
        commonSendPOSTRequestToApplication(path);
    }

    @Then("Send POST request to clear alarm at path {string}")
    public void sendPOSTRequestToClearAlarmAtPath(String path) throws Exception {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        commonSendPOSTRequestToApplication(path+"/"+ alarmDTO.getAlarmId());
    }

    @Then("Send POST request to acknowledge alarm at path {string}")
    public void sendPOSTRequestToAckAlarmAtPath(String path) throws Exception {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        commonSendPOSTRequestToApplication(path+"/"+ alarmDTO.getAlarmId()+"/blahUserId");
    }

    @Then("Send POST request to unacknowledge alarm at path {string}")
    public void sendPOSTRequestToUnAckAlarmAtPath(String path) throws Exception {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        commonSendPOSTRequestToApplication(path+"/"+ alarmDTO.getAlarmId());
    }

    @Then("Send POST request to set alarm severity at path {string}")
    public void sendPOSTRequestToSetAlarmSeverityAtPath(String path) throws Exception {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        commonSendPOSTRequestToApplication(path+"/"+ alarmDTO.getAlarmId()+"/1");
    }

    @Then("Send POST request to escalate alarm severity at path {string}")
    public void sendPOSTRequestToEscalateAlarmSeverityAtPath(String path) throws Exception {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        commonSendPOSTRequestToApplication(path+"/"+ alarmDTO.getAlarmId());
    }

    @Then("Send message to Kafka at topic {string}")
    public void sendMessageToKafkaAtTopic(String topic) throws Exception {
        URL requestUrl = new URL(new URL(this.kafkaRestBaseUrl), "/topics/" + topic);

        RestAssuredConfig restAssuredConfig = this.createRestAssuredTestConfig();

        RequestSpecification requestSpecification =
            RestAssured
                .given()
                .config(restAssuredConfig);

        Event event = Event.newBuilder().setNodeId(10L).setUei("BlahUEI").build();
        
        String body = formatKafkaRestProducerMessageBody(event.toByteArray());

        requestSpecification =
            requestSpecification
                .body(body)
                .header("Content-Type", "application/vnd.kafka.binary.v2+json")
                .header("Accept", "application/vnd.kafka.v2+json")
                ;

        restAssuredResponse =
            requestSpecification
                .post(requestUrl)
                .thenReturn()
        ;
    }

    @Then("delay")
    public void delay() throws InterruptedException{
        Thread.sleep(5000);
    }

    @Then("Verify the HTTP response code is {int}")
    public void verifyTheHTTPResponseCodeIs(int expectedStatusCode) {
        assertEquals(expectedStatusCode, restAssuredResponse.getStatusCode());
    }

//    @Then("delay {int}ms")
//    public void delayMs(int ms) throws Exception {
//        Thread.sleep(ms);
//    }

    @Then("Send GET request to application at path {string}")
    public void sendGETRequestToApplicationAtPath(String path) throws Exception {
        commonSendGetRequestToApplication(path);
    }

    @Then("Send DELETE request to application at path {string}")
    public void sendDELETERequestToApplicationAtPath(String path) throws Exception {
        log.info("####### sending POST to clear alarm {}", lastAlarmId);
        commonSendDELETERequestToApplication(path+"/"+lastAlarmId);
    }

    @Then("^parse the JSON response$")
    public void parseTheJsonResponse() {
        parsedJsonResponse = JsonPath.from((this.restAssuredResponse.getBody().asString()));
    }

    @Then("Verify JSON path expressions match$")
    public void verifyJsonPathExpressionsMatch(List<String> pathExpressions) {
        for (String onePathExpression : pathExpressions) {
            verifyJsonPathExpressionMatch(parsedJsonResponse, onePathExpression);
        }
    }

    @Then("Verify alarm was cleared")
    public void verifyAlarmWasCleared() {

        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        assertEquals(AlarmSeverity.CLEARED, alarmDTO.getSeverity());
    }

    @Then("Verify alarm was acknowledged")
    public void verifyAlarmWasAcked() {

        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        assertNotNull(alarmDTO.getAlarmAckTime());
        assertNotNull(alarmDTO.getAlarmAckUser());
    }

    @Then("Verify alarm was unacknowledged")
    public void verifyAlarmWasUnAcked() {

        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        assertNull(alarmDTO.getAlarmAckTime());
        assertNull(alarmDTO.getAlarmAckUser());
    }

    @Then("Verify alarm severity was escalated")
    public void verifyAlarmWasEscalated() {

        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        assertTrue(alarmDTO.getSeverity().isGreaterThan(AlarmSeverity.INDETERMINATE));
    }

    @Then("Verify alarm was uncleared")
    public void verifyAlarmWasUncleared() {

        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        assertEquals(alarmDTO.getLastEventSeverity(), alarmDTO.getSeverity());
    }

    //TODO:MMF need a better way to determine this. For now just assuming the second one.
    @Then("Remember alarm id")
    public void rememberAlarmId() {
        AlarmCollectionDTO alarmCollectionDTO = restAssuredResponse.getBody().as(AlarmCollectionDTO.class);
        AlarmDTO alarmDTO = alarmCollectionDTO.getAlarms().get(0);
        this.lastAlarmId = alarmDTO.getAlarmId();
    }

//========================================
// Utility Rules
//----------------------------------------

    @Then("^DEBUG dump the response body$")
    public void debugDumpTheResponseBody() {
        log.info("RESPONSE BODY = {}", restAssuredResponse.getBody().asString());
    }

//========================================
// Internals
//----------------------------------------

    private void verifyJsonPathExpressionMatch(JsonPath jsonPath, String pathExpression) {
        String[] parts = pathExpression.split(" == ", 2);

        if (parts.length == 2) {
            // Expression and value to match - evaluate as a string and compare
            String actualValue = jsonPath.getString(parts[0]);
            String actualTrimmed;

            if (actualValue != null) {
                actualTrimmed = actualValue.trim();
            }  else {
                actualTrimmed = null;
            }

            String expectedTrimmed = parts[1].trim();

            assertEquals("matching to JSON path " + parts[0], expectedTrimmed, actualTrimmed);
        } else {
            // Just an expression - evaluate as a boolean
            assertTrue("verifying JSON path expression " + pathExpression, jsonPath.getBoolean(pathExpression));
        }
    }

    private void commonSendGetRequestToApplication(String path) throws MalformedURLException {
        URL requestUrl = new URL(new URL(this.applicationBaseUrl), path);

        RestAssuredConfig restAssuredConfig = this.createRestAssuredTestConfig();

        RequestSpecification requestSpecification =
            RestAssured
                .given()
                .config(restAssuredConfig);

        restAssuredResponse =
            requestSpecification
                .get(requestUrl)
                .thenReturn()
        ;
    }
    private RestAssuredConfig createRestAssuredTestConfig() {
        return RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", DEFAULT_HTTP_SOCKET_TIMEOUT)
                .setParam("http.socket.timeout", DEFAULT_HTTP_SOCKET_TIMEOUT)
            );
    }

    private void commonSendPOSTRequestToApplication(String path) throws MalformedURLException {
        URL requestUrl = new URL(new URL(this.applicationBaseUrl), path);

        RestAssuredConfig restAssuredConfig = this.createRestAssuredTestConfig();

        RequestSpecification requestSpecification =
            RestAssured
                .given()
                .config(restAssuredConfig);

        restAssuredResponse =
            requestSpecification
                .post(requestUrl)
                .thenReturn();
    }

    private void commonSendDELETERequestToApplication(String path) throws MalformedURLException {
        URL requestUrl = new URL(new URL(this.applicationBaseUrl), path);

        RestAssuredConfig restAssuredConfig = this.createRestAssuredTestConfig();

        RequestSpecification requestSpecification =
            RestAssured
                .given()
                .config(restAssuredConfig);

        restAssuredResponse =
            requestSpecification
                .delete(requestUrl)
                .thenReturn();
    }

    private String formatKafkaRestProducerMessageBody(byte[] payload) throws JsonProcessingException {
        KafkaRestRecord record = new KafkaRestRecord();

        byte[] encoded = Base64.getEncoder().encode(payload);
        record.setValue(new String(encoded, StandardCharsets.UTF_8));

        KafkaRestProducerRequest request = new KafkaRestProducerRequest();
        request.setRecords(new KafkaRestRecord[] { record });

        return new ObjectMapper().writeValueAsString(request);
    }

    private static class KafkaRestProducerRequest {
        private KafkaRestRecord[] records;

        public KafkaRestRecord[] getRecords() {
            return records;
        }

        public void setRecords(KafkaRestRecord[] records) {
            this.records = records;
        }
    }

    private static class KafkaRestRecord {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
