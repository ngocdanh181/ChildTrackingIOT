const WebSocket = require('ws');
const logger = require('../utils/logger');

class WebSocketServer {
    constructor() {
        this.wss = null;
        this.devices = new Map();  // ESP32: deviceId -> ws
        this.clients = new Map();  // Android: deviceId -> ws
    }

    initialize(server) {
        this.wss = new WebSocket.Server({ server });

        this.wss.on('connection', (ws, req) => {
            const url = new URL(req.url, `ws://${req.headers.host}`);
            const deviceId = url.searchParams.get('deviceId');
            const clientType = url.searchParams.get('type');

            logger.info(`New WebSocket connection: ${clientType} for device ${deviceId}`);

            if (!deviceId) {
                logger.error('No deviceId provided');
                ws.close();
                return;
            }

            // Lưu connection theo deviceId
            if (clientType === 'esp32') {
                this.devices.set(deviceId, ws);
                logger.info(`ESP32 ${deviceId} connected`);
            } else if (clientType === 'android') {
                this.clients.set(deviceId, ws);
                logger.info(`Android client for ${deviceId} connected`);
            }

            ws.on('message', (data) => {
                if (clientType === 'esp32') {
                    const client = this.clients.get(deviceId);
                    if (client && client.readyState === WebSocket.OPEN) {
                        client.send(data);
                        logger.info(`Forwarded ${data.length} bytes to Android client ${deviceId}`);
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