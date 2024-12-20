const Location = require('../models/Location');
const Device = require('../models/Device');
const mqtt = require('../config/mqtt');
const logger = require('../utils/logger');

// Validate GPS coordinates
const isValidCoordinates = (lat, lng) => {
    return !isNaN(lat) && !isNaN(lng) && 
           lat >= -90 && lat <= 90 && 
           lng >= -180 && lng <= 180;
};

// Lấy vị trí mới nhất của thiết bị
exports.getLatestLocation = async (req, res) => {
    try {
        const { deviceId } = req.params;

        // Kiểm tra thiết bị tồn tại
        const device = await Device.findOne({ deviceId });
        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        const location = await Location.findOne({ deviceId })
            .sort({ timestamp: -1 })
            .select('deviceId latitude longitude timestamp metadata');

        if (!location) {
            return res.status(404).json({
                success: false,
                error: 'No location data found for this device'
            });
        }

        res.status(200).json({
            success: true,
            data: location
        });
    } catch (error) {
        logger.error('Error in getLatestLocation:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Bật tracking GPS
exports.startTracking = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { interval = 10 } = req.body;

        // Validate interval
        if (interval < 5 || interval > 3600) {
            return res.status(400).json({
                success: false,
                error: 'Interval must be between 5 and 3600 seconds'
            });
        }

        // Cập nhật trạng thái device
        await Device.findOneAndUpdate(
            { deviceId },
            { 
                isTracking: true,
                trackingInterval: interval,
                lastSeen: Date.now(),
                status: 'online'
            }
        );

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'start_tracking',
            interval: interval
        }));

        res.status(200).json({
            success: true,
            message: 'Started GPS tracking',
            data: {
                isTracking: true,
                trackingInterval: interval
            }
        });
    } catch (error) {
        logger.error('Error in startTracking:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Dừng tracking GPS
exports.stopTracking = async (req, res) => {
    try {
        const { deviceId } = req.params;

        // Cập nhật trạng thái device
        await Device.findOneAndUpdate(
            { deviceId },
            { 
                isTracking: false,
                lastSeen: Date.now()
            }
        );

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'stop_tracking'
        }));

        res.status(200).json({
            success: true,
            message: 'Stopped GPS tracking',
            data: {
                isTracking: false
            }
        });
    } catch (error) {
        logger.error('Error in stopTracking:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Xử lý dữ liệu GPS từ MQTT
exports.handleMQTTLocation = async (topic, message) => {
    try {
        const data = JSON.parse(message.toString());
        const { deviceId, latitude, longitude, speed, altitude, satellites, timestamp } = data;

        // Validate coordinates
        if (!isValidCoordinates(latitude, longitude)) {
            logger.error('Invalid GPS coordinates received:', { latitude, longitude });
            return;
        }

        // Validate deviceId
        const device = await Device.findOne({ deviceId });
        if (!device) {
            logger.error('Unknown device ID:', deviceId);
            return;
        }

        // Lưu vị trí mới
        await Location.create({
            deviceId,
            latitude,
            longitude,
            timestamp: timestamp ? new Date(timestamp) : new Date(),
            metadata: {
                speed,
                altitude,
                satellites,
                accuracy: satellites > 4 ? 'high' : 'low'
            }
        });

        logger.info(`Location updated for device ${deviceId}`);

    } catch (error) {
        logger.error('Error handling MQTT location:', error);
    }
};
