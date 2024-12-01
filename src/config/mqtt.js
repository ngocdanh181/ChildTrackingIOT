const mqtt = require('mqtt');
const logger = require('../utils/logger');

const MQTT_CONFIG = {
    keepalive: 60,
    clean: true,
    reconnectPeriod: 5000, // Try to reconnect every 5 seconds
    connectTimeout: 30 * 1000, // 30 seconds
    qos: 1, // At least once delivery
    retain: false,
    clientId: `iot_server_${Math.random().toString(16).slice(3)}`,
    username: process.env.MQTT_USERNAME,
    password: process.env.MQTT_PASSWORD,
    will: {
        topic: 'server/status',
        payload: 'offline',
        qos: 1,
        retain: true
    }
};

class MQTTClient {
    constructor() {
        this.client = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.isConnected = false;
    }

    connect() {
        try {
            this.client = mqtt.connect(process.env.MQTT_BROKER_URL, MQTT_CONFIG);
            this.setupEventHandlers();
        } catch (error) {
            logger.error('MQTT Connection Error:', error);
            this.handleReconnect();
        }
    }

    setupEventHandlers() {
        // Connection successful
        this.client.on('connect', () => {
            this.isConnected = true;
            this.reconnectAttempts = 0;
            logger.info('Connected to MQTT broker');
            
            // Publish online status
            this.client.publish('server/status', 'online', { 
                qos: 1, 
                retain: true 
            });
            
            this.subscribe();
        });

        // Connection lost
        this.client.on('close', () => {
            this.isConnected = false;
            logger.warn('Disconnected from MQTT broker');
            this.handleReconnect();
        });

        // Error occurred
        this.client.on('error', (error) => {
            logger.error('MQTT Error:', error);
            if (!this.isConnected) {
                this.handleReconnect();
            }
        });

        // Reconnect started
        this.client.on('reconnect', () => {
            logger.info('Attempting to reconnect to MQTT broker');
        });

        // Message received
        this.client.on('message', this.handleMessage.bind(this));
    }

    handleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            setTimeout(() => {
                logger.info(`Reconnect attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}`);
                this.connect();
            }, MQTT_CONFIG.reconnectPeriod);
        } else {
            logger.error('Max reconnection attempts reached');
            process.exit(1); // Exit process if can't connect to MQTT
        }
    }

    subscribe() {
        const topics = {
            'device/+/status': { qos: 1 },
            'device/+/telemetry': { qos: 1 },
            'device/+/audio': { qos: 1 },
            'device/+/location': { qos: 1 }
        };

        this.client.subscribe(topics, (err, granted) => {
            if (err) {
                logger.error('MQTT Subscribe Error:', err);
                return;
            }
            granted.forEach(({ topic, qos }) => {
                logger.info(`Subscribed to ${topic} with QoS ${qos}`);
            });
        });
    }

    handleMessage(topic, message) {
        try {
            const topicParts = topic.split('/');
            
            if (topicParts.length !== 3) {
                logger.warn(`Invalid topic format: ${topic}`);
                return;
            }

            const [prefix, deviceId, type] = topicParts;
            const payload = message.toString();

            // Log chi tiết message nhận được
            logger.info(`Received ${type} message from device ${deviceId}`);
            
            try {
                // Parse JSON payload
                const data = JSON.parse(payload);
                logger.info(`Data: ${JSON.stringify(data, null, 2)}`);
                
                // Route message to appropriate handler
                this.routeMessage(type, deviceId, data);
            } catch (parseError) {
                logger.warn(`Message is not JSON format: ${payload}`);
                // Vẫn xử lý message dạng string
                this.routeMessage(type, deviceId, payload);
            }
        } catch (error) {
            logger.error('Error handling MQTT message:', error);
        }
    }

    async routeMessage(type, deviceId, data) {
        try {
            // Import models
            const Device = require('../models/Device');
            const Audio = require('../models/Audio');
            const Location = require('../models/Location');

            // Update device last seen
            await Device.findOneAndUpdate(
                { deviceId },
                { 
                    $set: { 
                        lastSeen: new Date(),
                        status: type === 'status' ? data : undefined
                    }
                },
                { upsert: true }
            );

            // Handle different message types
            switch (type) {
                case 'status':
                    logger.info(`Device ${deviceId} status: ${typeof data === 'object' ? JSON.stringify(data) : data}`);
                    break;

                case 'telemetry':
                    logger.info(`Telemetry from ${deviceId}: ${typeof data === 'object' ? JSON.stringify(data) : data}`);
                    // Có thể thêm xử lý lưu telemetry data
                    break;

                case 'audio':
                    logger.info(`Received audio data from ${deviceId}`);
                    await Audio.create({
                        deviceId,
                        data: data,
                        timestamp: new Date()
                    });
                    break;

                case 'location':
                    logger.info(`Location update from ${deviceId}: ${typeof data === 'object' ? JSON.stringify(data) : data}`);
                    await Location.create({
                        deviceId,
                        ...data,
                        timestamp: new Date()
                    });
                    break;

                default:
                    logger.warn(`Unknown message type: ${type}`);
            }
        } catch (error) {
            logger.error(`Error routing ${type} message:`, error);
        }
    }

    publish(topic, message, options = {}) {
        return new Promise((resolve, reject) => {
            if (!this.isConnected) {
                reject(new Error('MQTT client not connected'));
                return;
            }

            const defaultOptions = {
                qos: 1,
                retain: false,
                ...options
            };

            this.client.publish(topic, message, defaultOptions, (err) => {
                if (err) {
                    logger.error('MQTT Publish Error:', err);
                    reject(err);
                } else {
                    resolve();
                }
            });
        });
    }

    disconnect() {
        if (this.client) {
            // Publish offline status before disconnecting
            this.client.publish('server/status', 'offline', { 
                qos: 1, 
                retain: true 
            }, () => {
                this.client.end(true, () => {
                    logger.info('MQTT client disconnected');
                });
            });
        }
    }
}

// Export singleton instance
module.exports = new MQTTClient();
