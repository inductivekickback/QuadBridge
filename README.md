# QuadBridge
An Android app for controlling a [Syma X4 (218 BKT)] (http://www.amazon.com/gp/product/B00K5YXTVI?keywords=syma%20x4&qid=1445138267&ref_=sr_1_3&sr=8-3) quadcopter via Bluetooth Low Energy using a Nordic nRF51 SOC as a BLE-to-ShockBurst bridge.

##About
The [quad_bridge_fw](https://github.com/inductivekickback/quad_bridge_fw) project turns the [nRF51-DK](http://www.digikey.com/product-detail/en/NRF51-DK/1490-1038-ND/5022449) into a BLE-to-ShockBurst bridge using the S110 SoftDevice's Multiprotocol Timeslot API. This app reads the device's accelerometer and uses it to send commands to the nRF51.
