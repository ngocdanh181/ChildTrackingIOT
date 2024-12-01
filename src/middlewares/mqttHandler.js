const mqtt = require('mqtt');
const deviceController = require('../controllers/deviceController');
const audioController = require('../controllers/audioController');
const locationController = require('../controllers/locationController');

class MQTTHandler {
    constructor() {
        this.client = null;
        this.connected = false;
    }

    connect() {
        // Kết nối tới MQTT broker
        this.client = mqtt.connect(process.env.MQTT_BROKER_URL, {
            username: process.env.MQTT_USERNAME,
            password: process.env.MQTT_PASSWORD,
            clientId: `iot_server_${Math.random().toString(16).slice(3)}`
        });

        // Xử lý sự kiện kết nối
        this.client.on('connect', () => {
            console.log('Connected to MQTT broker');
            this.connected = true;
            this.subscribeToTopics();
        });

        // Xử lý sự kiện mất kết nối
        this.client.on('close', () => {
            console.log('Disconnected from MQTT broker');
            this.connected = false;
        });

        // Xử lý lỗi
        this.client.on('error', (err) => {
            console.error('MQTT Error:', err);
            this.connected = false;
        });

        // Xử lý messages
        this.client.on('message', (topic, message) => {
            this.handleMessage(topic, message);
        });
    }

    // Subscribe vào các topics
    subscribeToTopics() {
        const topics = [
            'device/+/status',      // Device status updates
            'device/+/telemetry',   // Device telemetry data
            'device/+/audio',       // Audio data
            'device/+/location'     // Location data
        ];

        topics.forEach(topic => {
            this.client.subscribe(topic, (err) => {
                if (err) {
                    console.error(`Error subscribing to ${topic}:`, err);
                } else {
                    console.log(`Subscribed to ${topic}`);
                }
            });
        });
    }

    // Xử lý messages từ các topics
    handleMessage(topic, message) {
        const topicParts = topic.split('/');
        if (topicParts.length !== 3) return;

        const [prefix, deviceId, type] = topicParts;

        try {
            switch (type) {
                case 'status':
                    deviceController.handleMQTTConnection(topic, message);
                    break;
                case 'telemetry':
                    deviceController.handleMQTTTelemetry(topic, message);
                    break;
                case 'audio':
                    audioController.handleMQTTAudio(topic, message);
                    break;
                case 'location':
                    locationController.handleMQTTLocation(topic, message);
                    break;
                default:
                    console.warn('Unknown message type:', type);
            }
        } catch (error) {
            console.error('Error handling MQTT message:', error);
        }
    }

    // Publish message
    publish(topic, message) {
        if (!this.connected) {
            throw new Error('MQTT client not connected');
        }
        return new Promise((resolve, reject) => {
            this.client.publish(topic, message, (err) => {
                if (err) {
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
    }

    // Đóng kết nối
    disconnect() {
        if (this.client) {
            this.client.end();
        }
    }
}

// Tạo singleton instance
const mqttHandler = new MQTTHandler();

module.exports = mqttHandler; 