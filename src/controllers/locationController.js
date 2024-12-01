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
            .select('latitude longitude timestamp metadata');

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

// Lấy lịch sử vị trí của thiết bị
exports.getLocationHistory = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { startDate, endDate, limit = 100 } = req.query;
        
        // Validate dates
        if (startDate && !Date.parse(startDate) || endDate && !Date.parse(endDate)) {
            return res.status(400).json({
                success: false,
                error: 'Invalid date format'
            });
        }

        let query = { deviceId };
        if (startDate || endDate) {
            query.timestamp = {};
            if (startDate) query.timestamp.$gte = new Date(startDate);
            if (endDate) query.timestamp.$lte = new Date(endDate);
        }

        const locations = await Location.find(query)
            .sort({ timestamp: -1 })
            .limit(parseInt(limit))
            .select('latitude longitude timestamp metadata');

        res.status(200).json({
            success: true,
            count: locations.length,
            data: locations
        });
    } catch (error) {
        logger.error('Error in getLocationHistory:', error);
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

        // Cập nhật lastSeen của thiết bị
        await Device.findOneAndUpdate(
            { deviceId },
            {
                lastSeen: Date.now(),
                status: 'online'
            }
        );

        logger.info(`Location updated for device ${deviceId}`);

    } catch (error) {
        logger.error('Error handling MQTT location:', error);
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

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'start_tracking',
            interval: interval
        }));

        res.status(200).json({
            success: true,
            message: 'Started GPS tracking',
            interval: interval
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

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'stop_tracking'
        }));

        res.status(200).json({
            success: true,
            message: 'Stopped GPS tracking'
        });
    } catch (error) {
        logger.error('Error in stopTracking:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Lấy vị trí hiện tại
exports.getCurrentLocation = async (req, res) => {
    try {
        const { deviceId } = req.params;

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'get_location'
        }));

        res.status(202).json({
            success: true,
            message: 'Location request sent to device'
        });
    } catch (error) {
        logger.error('Error in getCurrentLocation:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Cập nhật interval tracking
exports.updateTrackingInterval = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { interval } = req.body;

        if (!interval || interval < 5 || interval > 3600) {
            return res.status(400).json({
                success: false,
                error: 'Interval must be between 5 and 3600 seconds'
            });
        }

        // Gửi lệnh qua MQTT
        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'set_tracking_interval',
            interval: interval
        }));

        res.status(200).json({
            success: true,
            message: 'Updated tracking interval',
            interval: interval
        });
    } catch (error) {
        logger.error('Error in updateTrackingInterval:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};
