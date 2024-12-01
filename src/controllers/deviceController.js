const Device = require('../models/Device');
const mqtt = require('mqtt');
const client = mqtt.connect(process.env.MQTT_BROKER_URL);

// Lấy tất cả thiết bị
exports.getAllDevices = async (req, res) => {
    try {
        const devices = await Device.find()
            .select('-settings.mqttPassword')  // Không trả về sensitive data
            .sort({ lastSeen: -1 });

        res.status(200).json({
            success: true,
            count: devices.length,
            data: devices
        });
    } catch (error) {
        console.error('Error in getAllDevices:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Lấy một thiết bị theo ID
exports.getDevice = async (req, res) => {
    try {
        const device = await Device.findOne({ deviceId: req.params.id })
            .select('-settings.mqttPassword');

        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        res.status(200).json({
            success: true,
            data: device
        });
    } catch (error) {
        console.error('Error in getDevice:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Tạo thiết bị mới
exports.createDevice = async (req, res) => {
    try {
        // Tạo MQTT topic cho thiết bị
        const mqttTopic = `device/${req.body.deviceId}`;
        const device = await Device.create({
            ...req.body,
            mqttTopic,
            status: 'offline'
        });

        // Subscribe to device topics
        client.subscribe(`${mqttTopic}/status`);
        client.subscribe(`${mqttTopic}/telemetry`);

        res.status(201).json({
            success: true,
            data: device
        });
    } catch (error) {
        console.error('Error in createDevice:', error);
        if (error.code === 11000) { // Duplicate key error
            return res.status(400).json({
                success: false,
                error: 'Device ID already exists'
            });
        }
        if (error.name === 'ValidationError') {
            const messages = Object.values(error.errors).map(val => val.message);
            return res.status(400).json({
                success: false,
                error: messages
            });
        }
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Cập nhật thiết bị
exports.updateDevice = async (req, res) => {
    try {
        const device = await Device.findOneAndUpdate(
            { deviceId: req.params.id },
            req.body,
            {
                new: true,
                runValidators: true
            }
        );

        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        // Nếu có thay đổi cấu hình, gửi cập nhật qua MQTT
        if (req.body.settings) {
            client.publish(`device/${device.deviceId}/config`, 
                JSON.stringify(req.body.settings)
            );
        }

        res.status(200).json({
            success: true,
            data: device
        });
    } catch (error) {
        console.error('Error in updateDevice:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Xóa thiết bị
exports.deleteDevice = async (req, res) => {
    try {
        const device = await Device.findOne({ deviceId: req.params.id });
        
        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        // Unsubscribe from MQTT topics
        client.unsubscribe(`device/${device.deviceId}/status`);
        client.unsubscribe(`device/${device.deviceId}/telemetry`);

        await device.remove();

        res.status(200).json({
            success: true,
            data: {}
        });
    } catch (error) {
        console.error('Error in deleteDevice:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Xử lý kết nối MQTT từ thiết bị
exports.handleMQTTConnection = async (topic, message) => {
    try {
        const deviceId = topic.split('/')[1];
        const status = message.toString();

        await Device.findOneAndUpdate(
            { deviceId },
            {
                status: status === 'connected' ? 'online' : 'offline',
                lastSeen: status === 'connected' ? Date.now() : undefined
            }
        );
    } catch (error) {
        console.error('Error handling MQTT connection:', error);
    }
};

// Xử lý telemetry từ thiết bị
exports.handleMQTTTelemetry = async (topic, message) => {
    try {
        const deviceId = topic.split('/')[1];
        const telemetry = JSON.parse(message.toString());

        await Device.findOneAndUpdate(
            { deviceId },
            {
                batteryLevel: telemetry.battery,
                signalStrength: telemetry.rssi,
                lastSeen: Date.now()
            }
        );
    } catch (error) {
        console.error('Error handling MQTT telemetry:', error);
    }
};

// Gửi lệnh restart thiết bị
exports.restartDevice = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const device = await Device.findOne({ deviceId });

        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        // Gửi lệnh restart qua MQTT
        client.publish(`device/${deviceId}/control`, 
            JSON.stringify({ command: 'restart' })
        );

        res.status(200).json({
            success: true,
            message: 'Restart command sent'
        });
    } catch (error) {
        console.error('Error in restartDevice:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

// Cập nhật firmware
exports.updateFirmware = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { firmwareUrl, version } = req.body;

        const device = await Device.findOne({ deviceId });
        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        // Gửi thông tin firmware mới qua MQTT
        client.publish(`device/${deviceId}/firmware`, 
            JSON.stringify({
                url: firmwareUrl,
                version: version
            })
        );

        res.status(200).json({
            success: true,
            message: 'Firmware update initiated'
        });
    } catch (error) {
        console.error('Error in updateFirmware:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};
