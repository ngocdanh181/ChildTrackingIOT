#include <WiFi.h>
#include <PubSubClient.h>
#include <TinyGPS++.h>
#include <ArduinoJson.h>
#include <driver/i2s.h>
#include <WebSocketsClient.h>
#include <SoftwareSerial.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"

// Network settings
const char* ssid = "P1506";
const char* password = "19011994";
const char* gprs_apn = "internet.vietnamobile.com.vn";
const char* gprs_user = "mms";
const char* gprs_pass = "mms";

// Server settings
const char* mqtt_server = "192.168.1.5";
const int mqtt_port = 1883;
const char* ws_host = "192.168.1.5";
const int ws_port = 3000;
const char* deviceId = "ESP32_001";

// Pin definitions
// GPS Module
#define GPS_RX 16
#define GPS_TX 17
#define GPS_BAUD 9600

// INMP441 Microphone
#define I2S_WS 25
#define I2S_SD 33
#define I2S_SCK 32
#define I2S_PORT I2S_NUM_0
#define I2S_SAMPLE_RATE 16000
#define I2S_SAMPLE_BITS 16
#define I2S_BUFFER_SIZE 256

// SIM800L Module
#define SIM800_TX 4  // ESP32 GPIO4 -> SIM800L RX
#define SIM800_RX 2  // ESP32 GPIO2 -> SIM800L TX
#define SIM800_RST 5 // ESP32 GPIO5 -> SIM800L RST

// State variables
bool isTracking = false;
bool isListening = false;
unsigned long lastGPSUpdate = 0;
unsigned long gpsInterval = 10000;
unsigned long lastNetworkCheck = 0;
const unsigned long NETWORK_CHECK_INTERVAL = 30000;

// Connection state
enum ConnectionType {
    NONE,
    WIFI,
    GPRS
};
ConnectionType activeConnection = NONE;

// Objects
SoftwareSerial simSerial(SIM800_RX, SIM800_TX);
WiFiClient wifiClient;
WiFiClient gprsClient;
PubSubClient mqtt;
WebSocketsClient webSocket;
TinyGPSPlus gps;
HardwareSerial gpsSerial(1);

// Định nghĩa handles cho tasks
TaskHandle_t gpsTaskHandle = NULL;
TaskHandle_t audioTaskHandle = NULL;
TaskHandle_t networkTaskHandle = NULL;

// Semaphores để đồng bộ hóa
SemaphoreHandle_t networkMutex;
SemaphoreHandle_t i2sMutex;

void setup() {
    Serial.begin(115200);
    
    // Khởi tạo semaphores
    networkMutex = xSemaphoreCreateMutex();
    i2sMutex = xSemaphoreCreateMutex();
    
    setupSIM800L();
    setupNetwork();
    setupGPS();
    setupI2S();
    
    // Tạo các tasks
    xTaskCreatePinnedToCore(
        networkTask,    // Task function
        "NetworkTask",  // Task name
        10000,         // Stack size
        NULL,          // Parameters
        2,             // Priority
        &networkTaskHandle,  // Task handle
        0              // Core ID (0)
    );
    
    xTaskCreatePinnedToCore(
        gpsTask,
        "GPSTask",
        5000,
        NULL,
        1,
        &gpsTaskHandle,
        1              // Core ID (1)
    );
    
    xTaskCreatePinnedToCore(
        audioTask,
        "AudioTask",
        10000,
        NULL,
        1,
        &audioTaskHandle,
        1              // Core ID (1)
    );
}

// Network task - xử lý MQTT và WebSocket
void networkTask(void * parameter) {
    for(;;) {
        if (xSemaphoreTake(networkMutex, portMAX_DELAY)) {
            ensureConnection();
            webSocket.loop();
            mqtt.loop();
            xSemaphoreGive(networkMutex);
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// GPS task
void gpsTask(void * parameter) {
    for(;;) {
        if (isTracking && gpsSerial.available()) {
            if (gps.encode(gpsSerial.read())) {
                if (millis() - lastGPSUpdate >= gpsInterval) {
                    if (xSemaphoreTake(networkMutex, portMAX_DELAY)) {
                        publishGPSData();
                        xSemaphoreGive(networkMutex);
                        lastGPSUpdate = millis();
                    }
                }
            }
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// Audio task
void audioTask(void * parameter) {
    for(;;) {
        if (isListening) {
            if (xSemaphoreTake(i2sMutex, portMAX_DELAY)) {
                size_t bytesRead = 0;
                uint8_t i2sBuffer[I2S_BUFFER_SIZE];
                
                if (i2s_read(I2S_PORT, i2sBuffer, I2S_BUFFER_SIZE, &bytesRead, portMAX_DELAY) == ESP_OK) {
                    if (bytesRead > 0) {
                        if (xSemaphoreTake(networkMutex, portMAX_DELAY)) {
                            webSocket.sendBIN(i2sBuffer, bytesRead);
                            xSemaphoreGive(networkMutex);
                        }
                    }
                }
                xSemaphoreGive(i2sMutex);
            }
        }
        vTaskDelay(pdMS_TO_TICKS(5));  // Shorter delay for audio
    }
}

void loop() {
    // Loop chính trống vì đã dùng FreeRTOS tasks
    vTaskDelay(portMAX_DELAY);
}

void setupSIM800L() {
    pinMode(SIM800_RST, OUTPUT);
    digitalWrite(SIM800_RST, HIGH);
    
    simSerial.begin(9600);
    delay(3000);
    
    Serial.println("Initializing SIM800L...");
    
    // Basic AT commands
    sendATCommand("AT");
    sendATCommand("AT+CFUN=1");
    
    // Wait for network registration
    int attempts = 0;
    while (attempts < 10) {
        if (sendATCommand("AT+CREG?").indexOf("+CREG: 0,1") > -1) {
            Serial.println("Registered to network");
            break;
        }
        delay(2000);
        attempts++;
    }
}

String sendATCommand(String command) {
    simSerial.println(command);
    delay(500);
    String response = "";
    while (simSerial.available()) {
        response += (char)simSerial.read();
    }
    Serial.println("AT Command: " + command);
    Serial.println("Response: " + response);
    return response;
}

bool connectWiFi() {
    Serial.println("Connecting to WiFi...");
    WiFi.begin(ssid, password);
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        Serial.print(".");
        attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("\nWiFi connected");
        return true;
    }
    
    Serial.println("\nWiFi connection failed");
    return false;
}

bool connectGPRS() {
    Serial.println("Connecting to GPRS...");
    
    sendATCommand("AT+SAPBR=3,1,\"Contype\",\"GPRS\"");
    sendATCommand("AT+SAPBR=3,1,\"APN\",\"" + String(gprs_apn) + "\"");
    
    if (strlen(gprs_user) > 0) {
        sendATCommand("AT+SAPBR=3,1,\"USER\",\"" + String(gprs_user) + "\"");
    }
    
    if (strlen(gprs_pass) > 0) {
        sendATCommand("AT+SAPBR=3,1,\"PWD\",\"" + String(gprs_pass) + "\"");
    }
    
    String response = sendATCommand("AT+SAPBR=1,1");
    delay(3000);
    
    response = sendATCommand("AT+SAPBR=2,1");
    if (response.indexOf("+SAPBR: 1,1") > -1) {
        Serial.println("GPRS connected");
        return true;
    }
    
    return false;
}

void ensureConnection() {
    if (activeConnection == WIFI && WiFi.status() != WL_CONNECTED) {
        Serial.println("WiFi connection lost");
        if (!connectWiFi()) {
            switchToGPRS();
        }
    } else if (activeConnection == GPRS) {
        if (connectWiFi()) {
            switchToWiFi();
        }
    }
}

void switchToGPRS() {
    if (connectGPRS()) {
        activeConnection = GPRS;
        mqtt.setClient(gprsClient);
        reconnectMQTT();
    } else {
        activeConnection = NONE;
    }
}

void switchToWiFi() {
    activeConnection = WIFI;
    mqtt.setClient(wifiClient);
    reconnectMQTT();
}

void setupMQTT() {
    mqtt.setServer(mqtt_server, mqtt_port);
    mqtt.setCallback(handleMQTTMessage);
}

void setupWebSocket() {
    String url = "/audio?type=esp32&deviceId=" + String(deviceId);
    webSocket.begin(ws_host, ws_port, url);
    
    Serial.println("Attempting WebSocket connection...");
    Serial.printf("WebSocket URL: ws://%s:%d%s\n", ws_host, ws_port, url.c_str());
    
    webSocket.onEvent([](WStype_t type, uint8_t * payload, size_t length) {
        switch(type) {
            case WStype_DISCONNECTED:
                Serial.println("WebSocket Disconnected!");
                break;
            case WStype_CONNECTED:
                Serial.println("WebSocket Connected!");
                break;
            case WStype_ERROR:
                Serial.println("WebSocket Error!");
                break;
        }
    });
}

void handleMQTTMessage(char* topic, byte* payload, unsigned int length) {
    char message[length + 1];
    memcpy(message, payload, length);
    message[length] = '\0';
    
    Serial.printf("Received message: %s\n", message);
    
    StaticJsonDocument<200> doc;
    DeserializationError error = deserializeJson(doc, message);
    
    if (error) {
        Serial.println("Failed to parse JSON command");
        return;
    }
    
    const char* command = doc["command"];
    Serial.printf("Command received: %s\n", command);
    
    if (strcmp(command, "start_tracking") == 0) {
        isTracking = true;
        gpsInterval = doc["interval"].as<long>() * 1000;
        Serial.printf("Started tracking with interval %ld ms\n", gpsInterval);
    }
    else if (strcmp(command, "stop_tracking") == 0) {
        isTracking = false;
        Serial.println("Stopped tracking");
    }
    else if (strcmp(command, "start_listening") == 0) {
        isListening = true;
        Serial.println("Started listening");
    }
    else if (strcmp(command, "stop_listening") == 0) {
        isListening = false;
        Serial.println("Stopped listening");
    }
}

void publishGPSData() {
    if (gps.location.isValid()) {
        StaticJsonDocument<200> doc;
        doc["deviceId"] = deviceId;
        doc["latitude"] = gps.location.lat();
        doc["longitude"] = gps.location.lng();
        doc["altitude"] = gps.altitude.meters();
        doc["speed"] = gps.speed.kmph();
        doc["satellites"] = gps.satellites.value();
        doc["timestamp"] = millis();
        
        char jsonBuffer[512];
        serializeJson(doc, jsonBuffer);
        
        mqtt.publish("device/ESP32_001/location", jsonBuffer);
        Serial.printf("Location sent: %.6f, %.6f\n", gps.location.lat(), gps.location.lng());
    }
}

void publishAudioData() {
    size_t bytesRead = 0;
    uint8_t i2sBuffer[I2S_BUFFER_SIZE];
    
    if (i2s_read(I2S_PORT, i2sBuffer, I2S_BUFFER_SIZE, &bytesRead, portMAX_DELAY) == ESP_OK) {
        if (bytesRead > 0) {
            // Debug audio levels
            int16_t* samples = (int16_t*)i2sBuffer;
            int samplesCount = bytesRead / 2;  // 16-bit = 2 bytes per sample
            
            // Calculate average and peak values
            int32_t sum = 0;
            int16_t peak = 0;
            for(int i = 0; i < samplesCount; i++) {
                sum += abs(samples[i]);
                if(abs(samples[i]) > peak) {
                    peak = abs(samples[i]);
                }
            }
            int16_t average = sum / samplesCount;
            
            // Print audio metrics
            Serial.printf("Audio Metrics - Bytes: %d, Samples: %d, Avg: %d, Peak: %d\n", 
                bytesRead, samplesCount, average, peak);
            
            // Print first few samples
            Serial.print("Sample values: ");
            for(int i = 0; i < 5 && i < samplesCount; i++) {
                Serial.printf("%d ", samples[i]);
            }
            Serial.println();

            // Send via WebSocket
            webSocket.sendBIN(i2sBuffer, bytesRead);
            Serial.println("Audio data sent via WebSocket");
        } else {
            Serial.println("No audio data read");
        }
    } else {
        Serial.println("Error reading from I2S");
    }
}

void reconnectMQTT() {
    while (!mqtt.connected()) {
        Serial.println("Attempting MQTT connection...");
        
        if (mqtt.connect(deviceId)) {
            Serial.println("Connected to MQTT broker");
            mqtt.subscribe("device/ESP32_001/control");
            
            // Publish online status
            StaticJsonDocument<200> doc;
            doc["status"] = "online";
            doc["isTracking"] = isTracking;
            doc["isListening"] = isListening;
            
            char jsonBuffer[512];
            serializeJson(doc, jsonBuffer);
            mqtt.publish("device/ESP32_001/status", jsonBuffer);
        } else {
            Serial.printf("Failed, rc=%d. Retrying in 5 seconds...\n", mqtt.state());
            delay(5000);
        }
    }
}

void setupGPS() {
    gpsSerial.begin(GPS_BAUD, SERIAL_8N1, GPS_RX, GPS_TX);
    Serial.println("GPS initialized");
}

void setupI2S() {
    esp_err_t err;

    const i2s_config_t i2s_config = {
        .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
        .sample_rate = I2S_SAMPLE_RATE,
        .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
        .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
        .communication_format = I2S_COMM_FORMAT_I2S,
        .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
        .dma_buf_count = 8,
        .dma_buf_len = I2S_BUFFER_SIZE,
        .use_apll = false,
        .tx_desc_auto_clear = false,
        .fixed_mclk = 0
    };

    const i2s_pin_config_t pin_config = {
        .bck_io_num = I2S_SCK,
        .ws_io_num = I2S_WS,
        .data_out_num = I2S_PIN_NO_CHANGE,
        .data_in_num = I2S_SD
    };

    err = i2s_driver_install(I2S_PORT, &i2s_config, 0, NULL);
    if (err != ESP_OK) {
        Serial.println("Failed to install I2S driver");
        return;
    }

    err = i2s_set_pin(I2S_PORT, &pin_config);
    if (err != ESP_OK) {
        Serial.println("Failed to set I2S pins");
        return;
    }

    Serial.println("I2S initialized");
}