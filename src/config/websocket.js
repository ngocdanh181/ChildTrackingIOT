const WebSocket = require('ws');
const logger = require('../utils/logger');

class WebSocketServer {
    constructor() {
        this.wss = null;
        this.devices = new Map();  // Map để lưu các ESP32 connections
        this.clients = new Map();  // Map để lưu các Android client connections
    }

    initialize(server) {
        this.wss = new WebSocket.Server({ 
            server,
            clientTracking: true,
            verifyClient: (info, cb) => {
                logger.info("WebSocket connection request from:", info.req.socket.remoteAddress);
                logger.info("Request URL:", info.req.url);
                cb(true);
            }
        });

        this.wss.on('listening', () => {
            logger.info('WebSocket server is listening');
        });

        this.wss.on('error', (error) => {
            logger.error('WebSocket server error:', error);
        });

        // Log khi WebSocket server khởi động
        logger.info('WebSocket server initializing...');

        this.wss.on('connection', (ws, req) => {
            const url = new URL(req.url, `ws://${req.headers.host}`);
            const deviceId = url.pathname.split('/').pop();
            const clientType = url.searchParams.get('type');

            logger.info('New WebSocket connection attempt:');
            logger.info(`URL: ${req.url}`);
            logger.info(`Client Type: ${clientType}`);
            logger.info(`Device ID: ${deviceId}`);
            logger.info(`Client IP: ${req.socket.remoteAddress}`);

            if (clientType === 'esp32') {
                this.devices.set(deviceId, ws);
                logger.info(`ESP32 ${deviceId} connected. Total ESP32 devices: ${this.devices.size}`);
            } else if (clientType === 'android') {
                this.clients.set(deviceId, ws);
                logger.info(`Android client for ${deviceId} connected. Total Android clients: ${this.clients.size}`);
            }

            ws.on('message', (data) => {
                if (clientType === 'esp32') {
                    // Log audio data metrics
                    logger.info(`Received audio data from ESP32 ${deviceId}. Size: ${data.length} bytes`);
                    
                    const client = this.clients.get(deviceId);
                    if (client && client.readyState === WebSocket.OPEN) {
                        client.send(data);
                        logger.info(`Forwarded audio data to Android client for ${deviceId}`);
                    } else {
                        logger.warn(`No active Android client for ${deviceId}`);
                    }
                }
            });

            ws.on('close', () => {
                if (clientType === 'esp32') {
                    this.devices.delete(deviceId);
                    logger.info(`ESP32 ${deviceId} disconnected. Remaining devices: ${this.devices.size}`);
                } else {
                    this.clients.delete(deviceId);
                    logger.info(`Android client for ${deviceId} disconnected. Remaining clients: ${this.clients.size}`);
                }
            });

            ws.on('error', (error) => {
                logger.error(`WebSocket error for ${clientType} ${deviceId}:`, error);
            });

            // Ping/Pong để giữ kết nối
            ws.on('pong', () => {
                logger.debug(`Received pong from ${clientType} ${deviceId}`);
            });

            // Gửi ping mỗi 30 giây
            const pingInterval = setInterval(() => {
                if (ws.readyState === WebSocket.OPEN) {
                    ws.ping();
                    logger.debug(`Sent ping to ${clientType} ${deviceId}`);
                } else {
                    clearInterval(pingInterval);
                }
            }, 30000);
        });

        logger.info('WebSocket server initialized successfully');
    }

    close() {
        if (this.wss) {
            this.wss.close(() => {
                logger.info('WebSocket server closed. Cleaning up connections...');
                this.devices.clear();
                this.clients.clear();
            });
        }
    }
}

module.exports = new WebSocketServer(); 