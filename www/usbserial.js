const exec = require('cordova/exec');

function UsbSerial() {
    this._port = null;
}

UsbSerial.prototype.list = function (callback) {
    exec(
        function (result) {
            callback(null, result);
        },
        function (error) {
            callback(error);
        },
        "UsbSerial",
        "listSerial",
        []
    );
};

UsbSerial.prototype.requestPermission = function (opts, callback) {
    exec(
        function (result) {
            callback(null, result);
        },
        function (error) {
            callback(error);
        },
        'UsbSerial',
        'requestPermission',
        [opts]
    );
};

/**
 * Driver list
 * requestPermission
 * FtdiSerialDriver
 * CdcAcmSerialDriver
 * Cp21xxSerialDriver
 * ProlificSerialDriver
 * Ch34xSerialDriver
 */
UsbSerial.prototype.open = function (opts, callback) {
    this._port = opts.port;
    this.requestPermission(
        opts,
        function () {
            exec(
                function (result) {
                    callback(null, result);
                },
                function (error) {
                    callback(error);
                },
                'UsbSerial',
                'openSerial',
                [opts]
            );
        },
        function (error) {
            callback(error);
        });
};

UsbSerial.prototype.write = function (data, callback) {
    exec(
        function (result) {
            callback(null, result);
        },
        function (error) {
            callback(error);
        },
        'UsbSerial',
        'writeSerial',
        [{data: data, port: this._port}]
    );
};

UsbSerial.prototype.close = function (callback) {
    exec(
        function (result) {
            callback(null, result);
        },
        function (error) {
            callback(error);
        },
        'UsbSerial',
        'closeSerial',
        [{port: this._port}]
    );
};

UsbSerial.prototype.registerReadCallback = function (callbackToRegister, callback) {
    exec(
        function(message) {
            callbackToRegister(arrayBufferToString(message));
        },
        function (error) {
            if (error === "") {
                callback();
            } else {
                callback(error);
            }
        },
        'UsbSerial',
        'registerReadCallback',
        [{port: this._port}]
    );
};

UsbSerial.prototype.isConnected = function (callback) {
    exec(
        function (result) {
            callback(null, result === "true");
        },
        function (error) {
            callback(error);
        },
        'UsbSerial',
        'isConnectedSerial',
        [{port: this._port}]
    );
};

function arrayBufferToString(buf) {
    return String.fromCharCode.apply(null, new Int8Array(buf));
}

console.info("Loaded Cordova UsbSerial plugin");

module.exports = new UsbSerial();
