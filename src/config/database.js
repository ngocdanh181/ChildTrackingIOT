const mongoose = require('mongoose');
const logger = require('../utils/logger');

const MONGO_OPTIONS = {
    useNewUrlParser: true,
    useUnifiedTopology: true,
    maxPoolSize: 10,
    serverSelectionTimeoutMS: 5000,
    socketTimeoutMS: 45000,
    family: 4,
    heartbeatFrequencyMS: 10000,
    autoIndex: process.env.NODE_ENV !== 'production', // Disable auto-indexing in production
};

const connectDB = async () => {
    try {
        const conn = await mongoose.connect(process.env.MONGODB_URI, MONGO_OPTIONS);

        logger.info(`MongoDB Connected: ${conn.connection.host}`);

        // Set up connection error handlers
        mongoose.connection.on('error', (err) => {
            logger.error('MongoDB connection error:', err);
        });

        mongoose.connection.on('disconnected', () => {
            logger.warn('MongoDB disconnected. Attempting to reconnect...');
        });

        mongoose.connection.on('reconnected', () => {
            logger.info('MongoDB reconnected');
        });

        // Handle process termination
        process.on('SIGINT', async () => {
            try {
                await mongoose.connection.close();
                logger.info('MongoDB connection closed through app termination');
                process.exit(0);
            } catch (err) {
                logger.error('Error closing MongoDB connection:', err);
                process.exit(1);
            }
        });

        // Optional: Create indexes in development
        if (process.env.NODE_ENV === 'development') {
            logger.info('Creating indexes...');
            await Promise.all([
                require('../models/Device').createIndexes(),
                require('../models/Audio').createIndexes(),
                require('../models/Location').createIndexes()
            ]);
            logger.info('Indexes created successfully');
        }

    } catch (error) {
        logger.error('Error connecting to MongoDB:', error);
        // Retry connection
        setTimeout(connectDB, 5000);
    }
};

// Export connection function
module.exports = connectDB;
