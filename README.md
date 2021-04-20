An Android app for controlling a Syma X4 (218 BKT) quadcopter via Bluetooth Low Energy using a Nordic nRF51 SOC as a BLE-to-ShockBurst bridge. More information about the project is [located here](http://inductivekickback.blogspot.com/2015/11/ble-to-shockburst-bridge-for-syma-x4.html).

<p align="center"><img src="https://user-images.githubusercontent.com/6494431/115438471-4a776c80-a1c2-11eb-84a3-33c2d0160a72.png"></p>

## About
The [quad_bridge_fw](https://github.com/inductivekickback/quad_bridge_fw) project turns the [nRF51-DK](http://www.digikey.com/product-detail/en/NRF51-DK/1490-1038-ND/5022449) into a BLE-to-ShockBurst bridge using the S110 SoftDevice's Multiprotocol Timeslot API. This app reads the device's accelerometer and uses it to send commands to the nRF51.
