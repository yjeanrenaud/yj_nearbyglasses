# yj_nearbyglasses
attempting to detect smart glasses nearby.

# Nearby Glasses 
The app <i>Nearby Glasses</i> has one sole purpose: look for smart glasses nearby and warn you.

This app notifies you when smart glasses are nearby. It uses company identificators in the Bluetooth data sent out by these. Therefore, there likely are false positives (e.g. from VR headsets). Hence, please proceed with caution when approaching a person nearby wearing glasses. They might just be regular glasses, despite this app’s warning.
        
The app’s author [Yves Jeanrenaud](https://yves.app) takes no liability whatsoever for this app nor it’s functionality. Use at your own risk. By technical design, detecting Bluetooth LE devices might sometimes just not work as expected. I am no graduated developer. This is all written in my free time and with knowledge I tought myself.<br/>
**False positives are likely.** This means, the app *Nearby Glasses* may notify you of smart glasses nearby when there might be in fact a VR headset of the same manufacturer or another product of that company’s breed. It may also miss smart glasses nearby. Again: I am no pro developer.<br/>
However, this app is free and open source (foss), you may review the code, change it and re-use it (under the [license](LICENSE)).<br/>
The app *Nearby Glasses* does not store any details about you or collects any information about you or your phone. There is no telemetry, no ads, and no other nuissance. If you install the app via Play Store, Google may know something about you and collect some stats. But the app itsself does not. <br/>
If you choose to store (export) the logfile, that is completely up to you and your liability where this data go to. The logs are recorded only locally and not automatically shared with anyone.<br/>
<br/>
**Use with extreme caution!** As stated before: There is no guarantee, detected smart glasses are really nearby. It might be another device looking technically (on the BTLE adv level) similar to smart glasses.<br/>
Please do not act rashly. **Think before you act upon any messages** (not only from this app).<br/>
<br/>
**App Icon**: The icon is based on [Eyeglass icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/eyeglass)<br/>
**License**:  This app *Nearby Glasses* is licensed under [PolyForm Noncommercial License 1.0.0](LICENSE).<br/>
<br/>
## Why?
- Because I consider smart glasses an intolerable intrusion, consent neglecting, horrible piece of tech that is already used for making various and tons of equally truely disgusting 'content'. [1](https://www.404media.co/border-patrol-agent-recorded-raid-with-metas-ray-ban-smart-glasses/), [2](https://www.404media.co/metas-ray-ban-glasses-users-film-and-harass-massage-parlor-workers/)
- Some smart glasses feature small LED signifing a recording is going on. But this is easily disabled, whilst Meta claims to prevent that. [3](https://www.404media.co/how-to-disable-meta-rayban-led-light/)
- Smart glasses have been used for instant facial recognition before [4](https://www.404media.co/someone-put-facial-recognition-tech-onto-metas-smart-glasses-to-instantly-dox-strangers/) and repordedly will be out of the box [5](https://www.nytimes.com/2026/02/13/technology/meta-facial-recognition-smart-glasses.html).

## How?
- It's a simple heurisitc approach. Because BTLE uses randomised MAC and the OSSID are not stable, nor the UUID of the service annoucement, you can't just scan for the bluetooth beacons. And, to make thinks even more dire, Meta uses proprietary Bluetooth services and UUIDs are not stable, ~~we can only rely on the communicated device names for now~~.
- The currently **most viable approach** comes from the [Bluetooth SIG assigned numbers repo](www.bluetooth.com/specifications/assigned-numbers/). Therefore, the manufacturer company's name shows up in the packet advertising header (ADV) of BTLE beacons. 
 - this is what BTLE advertising frames look like:
```
Frame 1: Advertising (ADV_IND)
Time:  0.591232 s
Address: C4:7C:8D:1E:2B:3F (Random Static)
RSSI: -58 dBm

Flags:
  02 01 06
    Flags: LE General Discoverable Mode, BR/EDR Not Supported

Manufacturer Specific Data:
  Length: 0x1A
  Type:   Manufacturer Specific Data (0xFF)
  Company ID: 0x058E (Meta Platforms Technologies, LLC)
  Data: 4D 45 54 41 5F 52 42 5F 47 4C 41 53 53

Service UUIDs:
  Complete List of 16-bit Service UUIDs
  0xFEAA
```
- According to [Bluetooth SIG assigned numbers repo](www.bluetooth.com/specifications/assigned-numbers/), we may use these company IDs:
  - `0x01AB` for `Meta Platforms, Inc. (formerly Facebook)`
  - `0x058E` for `Meta Platforms Technologies, LLC`
  - `0x0D53` for `Luxottica Group S.p.A`
- When the app recognised a BTLE device with a sufficiant signal strength, it will push an alert message.

## Features
- The app *Nearby Glasses* shows a notification when smart glasses are nearby (that means, a BTLE device of one of those three company IDs mentioned above)
- **Nearby** means, the RSSI (signal strength) is less than or equal to a given value: -75 dBm (default). This corresponds to a medium distance and an ok-ish signal. Let me explain:<br/>
 RSSI depends mainly on<br/>
  - Device transmit power
  - Antenna design
  - Walls and obstacles
  - Human bodies absorbing signal
  - Reflection and interference
  - Device orientation
But typical BLE (Bluetooth Low Energy) scenarios, RSSI rough distance (open space)<br/>
-60 dBm ~ 1 – 3 m<br/>
-70 dBm ~ 3 – 10 m<br/>
-80 dBm ~ 10 – 20 m<br/>
-90 dBm ~ 20 – 40 m<br/>
-100 dBm ~ 30 – 100+ m or near signal loss<br/>
Indoors, distances are often much shorter.<br/>
RSSI drops roughly according to<br/>
    `RSSI ≈ -10 * n * log10(distance) + constant`<br/>
- Therefore, the default RSSI threshold of -75 dBm corresponds to about 10 to 15 meters in open space. You got a good chance to spot that smart glasses wearing person like that.
- *Nearby Glasses* shows a debug log that is exportable (as txt file) and features a copy/passte function. Those are for advanced users.
- Under *Settings*, you may specify the log length, the debugging (display all scan items or only ADV frames)
- You may also enter your own **company IDs** as string of hex values, e.g. `"0x01AB,0x058E,0x0D53`. This overrides the built-in detection so your notification shows up for the new value(s)
- For better persistence, it uses *Foreground Service*. You may disable this under *Settings* if you don't need it
- The *Notification Cooldown* under *Settings* specififies how much time must pass between two warnings. Default is 10000 ms, which is 10 s.

## Todos
- It's now working in the lab! I need to debug it more with actuall smart glasses in the field.
- See [Releases](https://github.com/yjeanrenaud/yj_nearbyglasses/releases) for APK to download. 
- I will push this app to Google Play, too. I still have some developer certificate around I could use for that. I will also always publish releases  here on GitHub and elsewhere for those that avoid the Play Store.
- I am no BT or Android expert at all. For what I've learned, one could also dig deeper into the communication of the Meta Ray-Bans sniffing the BTLE traffic. By that, we would not need to rely on the device powering up or connecting bout could also use heurisitcs on the encrypted traffic transimssions without much false positives. But I haven't looked into BT traffic packets for more than ten years. I'm glad I remembered ADV frames...
- move all hard-coded texts into `strings.xml` for easier localisation.
- an iOS app would be easy to adapt, too. But I don't have the toolchain anymore.
