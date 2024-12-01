require('dotenv').config();
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const helmet = require('helmet');
const compression = require('compression');
const http = require('http');
const rateLimit = require('express-rate-limit');

const logger = require('./utils/logger');
const connectDB = require('./config/database');
const mqttHandler = require('./middlewares/mqttHandler');
const errorHandler = require('./middlewares/errorHandler');
const { apiLimiter } = require('./middlewares/rateLimiter');

// Import routes
const deviceRoutes = require('./routes/deviceRoutes');
const locationRoutes = require('./routes/locationRoutes');
const audioRoutes = require('./routes/audioRoutes');

// Initialize express
const app = express();
const server = http.createServer(app);

// Connect to Database
connectDB();

// Connect to MQTT Broker
mqttHandler.connect();

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

// Health check route
app.get('/health', (req, res) => {
    res.status(200).json({
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// Welcome route
app.get('/', (req, res) => {
    res.json({
        message: 'Welcome to IoT Tracking API',
        version: process.env.API_VERSION || '1.0.0',
        docs: '/api-docs'  // If you add Swagger documentation
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
const server_instance = server.listen(PORT, () => {
    logger.info(`Server running in ${process.env.NODE_ENV} mode on port ${PORT}`);
});

// Graceful shutdown
process.on('SIGTERM', gracefulShutdown);
process.on('SIGINT', gracefulShutdown);

async function gracefulShutdown() {
    logger.info('Received shutdown signal');

    // Close server
    server_instance.close(() => {
        logger.info('HTTP server closed');
    });

    // Disconnect MQTT
    mqttHandler.disconnect();
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
