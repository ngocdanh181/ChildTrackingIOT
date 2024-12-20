const Device = require('../models/Device');
const mqtt = require('../config/mqtt');
const logger = require('../utils/logger');

exports.startListening = async (req, res) => {
    try {
        const { deviceId } = req.params;

        const device = await Device.findOne({ deviceId });
        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'start_listening'
        }));

        res.status(200).json({
            success: true,
            data: { isListening: true }
        });
    } catch (error) {
        logger.error('Error in startListening:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

exports.stopListening = async (req, res) => {
    try {
        const { deviceId } = req.params;

        mqtt.publish(`device/${deviceId}/control`, JSON.stringify({
            command: 'stop_listening'
        }));

        res.status(200).json({
            success: true,
            data: { isListening: false }
        });
    } catch (error) {
        logger.error('Error in stopListening:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};

exports.getListeningStatus = async (req, res) => {
    try {
        const { deviceId } = req.params;
        
        const audio = await Audio.findOne({ deviceId });
        
        if (!audio) {
            return res.status(200).json({
                success: true,
                data: {
                    isListening: false,
                    sampleRate: 16000,
                    channelCount: 1,
                    encoding: 2
                }
            });
        }

        res.status(200).json({
            success: true,
            data: {
                isListening: audio.isListening,
                sampleRate: audio.sampleRate,
                channelCount: audio.channelCount,
                encoding: audio.encoding
            }
        });
    } catch (error) {
        logger.error('Error in getListeningStatus:', error);
        res.status(500).json({
            success: false,
            error: 'Server Error'
        });
    }
};