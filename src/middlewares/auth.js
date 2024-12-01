const jwt = require('jsonwebtoken');

exports.protect = async (req, res, next) => {
    try {
        let token;

        if (req.headers.authorization && 
            req.headers.authorization.startsWith('Bearer')) {
            token = req.headers.authorization.split(' ')[1];
        }

        if (!token) {
            return res.status(401).json({
                success: false,
                error: 'Not authorized to access this route'
            });
        }

        try {
            const decoded = jwt.verify(token, process.env.JWT_SECRET);
            req.device = decoded.id;
            next();
        } catch (err) {
            return res.status(401).json({
                success: false,
                error: 'Token is not valid'
            });
        }
    } catch (error) {
        res.status(500).json({
            success: false,
            error: 'Server Error in auth middleware'
        });
    }
};

exports.mqttAuth = (client, username, password, callback) => {
    // Implement MQTT authentication logic here
    const authorized = username === process.env.MQTT_USERNAME && 
                      password.toString() === process.env.MQTT_PASSWORD;
    callback(null, authorized);
};
