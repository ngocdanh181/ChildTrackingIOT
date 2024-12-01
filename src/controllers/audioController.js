const Audio = require('../models/Audio');
const Device = require('../models/Device');
const mqtt = require('mqtt');
const client = mqtt.connect(process.env.MQTT_BROKER_URL);
const fs = require('fs');
const path = require('path');
const { Buffer } = require('buffer');

// Lưu trữ các phiên ghi âm đang hoạt động
const activeRecordings = new Map();

// Khởi tạo phiên ghi âm mới
exports.startRecording = async (req, res) => {
    try {
        const { deviceId, duration = 30 } = req.body; // Mặc định 30 giây

        // Kiểm tra thiết bị
        const device = await Device.findOne({ deviceId });
        if (!device) {
            return res.status(404).json({
                success: false,
                error: 'Device not found'
            });
        }

        // Kiểm tra xem có đang ghi âm không
        if (activeRecordings.has(deviceId)) {
            return res.status(400).json({
                success: false,
                error: 'Recording already in progress'
            });
        }

        // Khởi tạo session mới
        const recordingSession = {
            startTime: Date.now(),
            duration: duration * 1000, // Chuyển sang milliseconds
            chunks: new Map(),
            totalChunks: 0,
            status: 'recording',
            metadata: null,
            deviceId
        };
        activeRecordings.set(deviceId, recordingSession);

        // Gửi lệnh bắt đầu ghi âm qua MQTT
        client.publish(`device/${deviceId}/control`, 
            JSON.stringify({ 
                command: 'start_recording',
                duration,
                timestamp: Date.now()
            })
        );

        res.status(200).json({
            success: true,
            message: 'Recording started',
            sessionId: deviceId,
            startTime: recordingSession.startTime
        });
    } catch (error) {
        console.error('Error starting recording:', error);
        res.status(500).json({
            success: false,
            error: 'Error starting recording'
        });
    }
};

// Dừng ghi âm
exports.stopRecording = async (req, res) => {
    try {
        const { deviceId } = req.params;
        
        if (!activeRecordings.has(deviceId)) {
            return res.status(404).json({
                success: false,
                error: 'No active recording found'
            });
        }

        client.publish(`device/${deviceId}/control`, 
            JSON.stringify({ 
                command: 'stop_recording',
                timestamp: Date.now()
            })
        );

        res.status(200).json({
            success: true,
            message: 'Stop command sent'
        });
    } catch (error) {
        console.error('Error stopping recording:', error);
        res.status(500).json({
            success: false,
            error: 'Error stopping recording'
        });
    }
};

// Xử lý chunks audio từ MQTT
exports.handleMQTTAudio = async (topic, message) => {
    try {
        const topicParts = topic.split('/');
        const deviceId = topicParts[1];
        const messageType = topicParts[3]; // metadata, data, hoặc status

        const session = activeRecordings.get(deviceId);
        if (!session) return; // Bỏ qua nếu không có phiên ghi âm

        switch (messageType) {
            case 'metadata':
                handleMetadata(deviceId, message);
                break;
            case 'data':
                handleAudioChunk(deviceId, message);
                break;
            case 'status':
                handleStatus(deviceId, message);
                break;
        }
    } catch (error) {
        console.error('Error handling MQTT audio:', error);
    }
};

// Xử lý metadata
const handleMetadata = (deviceId, message) => {
    const session = activeRecordings.get(deviceId);
    if (!session) return;

    const metadata = JSON.parse(message.toString());
    session.metadata = metadata;
    session.currentChunkId = metadata.chunkId;
};

// Xử lý từng chunk audio
const handleAudioChunk = async (deviceId, message) => {
    const session = activeRecordings.get(deviceId);
    if (!session) return;

    const chunk = message.toString(); // Base64 encoded chunk
    session.chunks.set(session.currentChunkId, chunk);
    session.totalChunks++;

    // Broadcast chunk to connected clients (nếu có WebSocket)
    // broadcastAudioChunk(deviceId, chunk);

    // Lưu chunk vào database
    try {
        await Audio.create({
            deviceId,
            chunkId: session.currentChunkId,
            data: chunk,
            timestamp: Date.now(),
            metadata: session.metadata
        });
    } catch (error) {
        console.error('Error saving audio chunk:', error);
    }
};

// Xử lý status messages
const handleStatus = async (deviceId, message) => {
    const session = activeRecordings.get(deviceId);
    if (!session) return;

    const status = JSON.parse(message.toString());

    if (status.status === 'stopped') {
        // Kết thúc phiên ghi âm
        session.status = 'completed';
        session.endTime = Date.now();
        
        // Lưu bản ghi âm vào database
        try {
            const audioRecord = await Audio.create({
                deviceId,
                fileName: `audio_${deviceId}_${session.startTime}.wav`,
                duration: (session.endTime - session.startTime) / 1000,
                totalChunks: session.totalChunks,
                metadata: session.metadata,
                status: 'completed'
            });

            // Xóa session
            activeRecordings.delete(deviceId);

        } catch (error) {
            console.error('Error saving audio record:', error);
        }
    }
};

// Lấy danh sách bản ghi âm của một thiết bị
exports.getAudioRecords = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { startDate, endDate } = req.query;

        let query = { deviceId };
        
        if (startDate || endDate) {
            query.createdAt = {};
            if (startDate) query.createdAt.$gte = new Date(startDate);
            if (endDate) query.createdAt.$lte = new Date(endDate);
        }

        const audioRecords = await Audio.find(query)
            .sort({ createdAt: -1 });

        res.status(200).json({
            success: true,
            count: audioRecords.length,
            data: audioRecords
        });
    } catch (error) {
        console.error('Error fetching audio records:', error);
        res.status(500).json({
            success: false,
            error: 'Error fetching audio records'
        });
    }
};

// Lấy trạng thái ghi âm hiện tại
exports.getRecordingStatus = async (req, res) => {
    try {
        const { deviceId } = req.params;
        const session = activeRecordings.get(deviceId);

        if (!session) {
            return res.status(200).json({
                success: true,
                isRecording: false
            });
        }

        res.status(200).json({
            success: true,
            isRecording: true,
            startTime: session.startTime,
            duration: session.duration,
            totalChunks: session.totalChunks,
            status: session.status
        });
    } catch (error) {
        console.error('Error getting recording status:', error);
        res.status(500).json({
            success: false,
            error: 'Error getting recording status'
        });
    }
};

// Xóa bản ghi âm
exports.deleteAudioRecord = async (req, res) => {
    try {
        const audio = await Audio.findById(req.params.id);
        if (!audio) {
            return res.status(404).json({
                success: false,
                error: 'Audio record not found'
            });
        }

        await audio.remove();

        res.status(200).json({
            success: true,
            message: 'Audio record deleted'
        });
    } catch (error) {
        console.error('Error deleting audio record:', error);
        res.status(500).json({
            success: false,
            error: 'Error deleting audio record'
        });
    }
};
