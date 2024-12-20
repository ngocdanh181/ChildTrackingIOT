#include <WiFi.h>
#include <PubSubClient.h>
#include <TinyGPS++.h>
#include <ArduinoJson.h>
#include <driver/i2s.h>
#include <base64.h>
#include <WebSocketsClient.h>

// WiFi credentials
const char* ssid = "P1506";
const char* password = "19011994";


// MQTT Broker settings
const char* mqtt_server = "192.168.1.5";
const int mqtt_port = 1883;

//WebSocket Settings
const char* ws_host = "192.168.1.5";
const int ws_port = 8080;


// Device settings
const char* deviceId = "ESP32_001";

// GPS settings (NEO-6M)
#define GPS_RX 16
#define GPS_TX 17
#define GPS_BAUD 9600

// I2S settings for INMP441
#define I2S_WS 25
#define I2S_SD 33
#define I2S_SCK 32
#define I2S_PORT I2S_NUM_0
#define I2S_SAMPLE_RATE 16000
#define I2S_SAMPLE_BITS 16
#define I2S_BUFFER_SIZE 256  // Giảm từ 512 xuống 256

// State variables
bool isTracking = false;
bool isListening = false;
unsigned long lastGPSUpdate = 0;
unsigned long gpsInterval = 10000; // 10 seconds default

// Objects
WiFiClient espClient;
PubSubClient mqtt(espClient);
TinyGPSPlus gps;
HardwareSerial gpsSerial(1);  // UART1 for GPS
WebSocketsClient webSocket;

// Function declarations
void setupWiFi();
void setupGPS();
void setupWebSocket();
void setupI2S();
void setupMQTT();
void handleMQTTMessage(char* topic, byte* payload, unsigned int length);
void publishGPSData();
void publishAudioData();
void reconnectMQTT();

void setup() {
    Serial.begin(115200);
    
    setupWiFi();
    setupGPS();
    setupI2S();
    setupMQTT();
    setupWebSocket();
}

void loop() {
  webSocket.loop();
    if (!mqtt.connected()) {
        reconnectMQTT();
    }
    mqtt.loop();

    // Handle GPS
    while (gpsSerial.available() > 0) {
        if (gps.encode(gpsSerial.read())) {
            if (isTracking && (millis() - lastGPSUpdate >= gpsInterval)) {
                publishGPSData();
                lastGPSUpdate = millis();
            }
        }
    }

    // Handle Audio
    if (isListening) {
        publishAudioData();
    }
}

void setupWiFi() {
    Serial.println("Connecting to WiFi...");
    WiFi.begin(ssid, password);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    
    Serial.println("\nWiFi connected");
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