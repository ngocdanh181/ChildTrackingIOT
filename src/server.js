require('dotenv').config();
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const helmet = require('helmet');
const compression = require('compression');
const http = require('http');
const rateLimit = require('express-rate-limit');
const mongoose = require('mongoose');

const logger = require('./utils/logger');
const connectDB = require('./config/database');
const mqttClient = require('./config/mqtt');
const errorHandler = require('./middlewares/errorHandler');
const { apiLimiter } = require('./middlewares/rateLimiter');
const websocket = require('./config/websocket');

// Import routes
const deviceRoutes = require('./routes/deviceRoutes');
const locationRoutes = require('./routes/locationRoutes');
const audioRoutes = require('./routes/audioRoutes');
const authRoutes = require('./routes/authRoutes');

// Initialize express
const app = express();
const server = http.createServer(app);

// Initialize services
async function initializeServices() {
    try {
        // Connect to MongoDB
        await connectDB();
        logger.info('MongoDB connected successfully');

        // Connect to MQTT Broker
        mqttClient.connect();
        
        // Wait for MQTT connection
        await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('MQTT connection timeout'));
            }, 10000); // 10 seconds timeout

            const checkConnection = setInterval(() => {
                if (mqttClient.isConnected) {
                    clearInterval(checkConnection);
                    clearTimeout(timeout);
                    resolve();
                }
            }, 100);
        });

        logger.info('MQTT connected successfully');
        return true;
    } catch (error) {
        logger.error('Failed to initialize services:', error);
        return false;
    }
}

// Initialize WebSocket server
websocket.initialize(server);


// Security Middleware
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            defaultSrc: ["'self'"],
            styleSrc: ["'self'", "'unsafe-inline'"],
            scriptSrc: ["'self'"],
            imgSrc: ["'self'", "data:", "https:"],
        },
    },
    crossOriginEmbedderPolicy: true,
    crossOriginOpenerPolicy: true,
    crossOriginResourcePolicy: { policy: "cross-origin" }
}));

// CORS configuration
app.use(cors({
    origin: process.env.ALLOWED_ORIGINS?.split(',') || '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
    credentials: true
}));

// General middleware
app.use(compression());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(morgan('combined', { stream: { write: message => logger.info(message.trim()) } }));

// Apply rate limiting to all routes
app.use('/api', apiLimiter);

// API Routes
app.use('/api/devices', deviceRoutes);
app.use('/api/locations', locationRoutes);
app.use('/api/audio', audioRoutes);
app.use('/api/auth', authRoutes);

// Health check route
app.get('/health', (req, res) => {
    res.status(200).json({
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: process.uptime(),
        mqtt: mqttClient.isConnected ? 'connected' : 'disconnected'
    });
});

// Welcome route
app.get('/', (req, res) => {
    res.json({
        message: 'Welcome to IoT Tracking API',
        version: process.env.API_VERSION || '1.0.0',
        docs: '/api-docs'
    });
});

// 404 handler
app.use((req, res) => {
    res.status(404).json({
        success: false,
        error: 'Route not found'
    });
});

// Error handling middleware
app.use(errorHandler);

// Start server
const PORT = process.env.PORT || 3000;

initializeServices().then(success => {
    if (success) {
        server.listen(PORT, () => {
            logger.info(`Server running in ${process.env.NODE_ENV} mode on port ${PORT}`);
            logger.info(`WebSocket server running on ws://localhost:${PORT}`);
        });
    } else {
        logger.error('Failed to start server due to initialization errors');
        process.exit(1);
    }
});

// Graceful shutdown
process.on('SIGTERM', () => gracefulShutdown());
process.on('SIGINT', () => gracefulShutdown());

async function gracefulShutdown() {
    logger.info('Received shutdown signal');

    // Close server
    server.close(() => {
        logger.info('HTTP server closed');
    });

    // Disconnect MQTT
    mqttClient.disconnect();
    logger.info('MQTT client disconnected');

    // Close database connection
    try {
        await mongoose.connection.close();
        logger.info('Database connection closed');
        process.exit(0);
    } catch (err) {
        logger.error('Error during shutdown:', err);
        process.exit(1);
    }
}

// Unhandled rejection handler
process.on('unhandledRejection', (err) => {
    logger.error('Unhandled Rejection:', err);
    // Don't exit the process, just log the error
});

module.exports = { app, server };
