# yj_nearbyglasses
attempting detect Meta Ray-Ban smart glasses nearby

## why?
- Because I consider Meta Ray-Bans a intolerable intrusion, consent neglecting, horrible piece of tech that is already used for making various and tons of equally truely disgusting 'content'. [1](https://www.404media.co/border-patrol-agent-recorded-raid-with-metas-ray-ban-smart-glasses/), [2](https://www.404media.co/metas-ray-ban-glasses-users-film-and-harass-massage-parlor-workers/)
- Their small LED signifing a recording is going on is easily disabled, whilst Meta claims to prevent that. [3](https://www.404media.co/how-to-disable-meta-rayban-led-light/)
- Meta Ray-Ban smart glasses have been used for instant facial recognition before [4](https://www.404media.co/someone-put-facial-recognition-tech-onto-metas-smart-glasses-to-instantly-dox-strangers/) and repordedly will be out of the box [5](https://www.nytimes.com/2026/02/13/technology/meta-facial-recognition-smart-glasses.html).

## how?
- It's a simple heurisitc approach. Because BTLE uses randomised MAC and the OSSID are not stable, nor the UUID of the service annoucement, I can't just scan for the bluetooth beacons. And, to make thinks even more dire, Meta uses proprietary Bluetooth services and UUIDs are not stable, we can only rely on the communicated device names for now.
- When the app recognised a BTLE device with a sufficiant signal strength, it will push an alert message.

## todo

- dig deeper into the [BT SIG's registered numbers dir](https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/).
- It's not working yet on the repo nor is the code complete. I have a prototype app here running and did not upload all code yet due to lack of time. As soon as I get some spare time and figure some last things out, I will push this app to google play, too. I still have some developer certificate around I could use for that. I will also pusblsh releases as APK here on GitHub and elsewhere.
- I am no BT expert at all. For what I've learned, one could also dig deeper into the communication of the Meta Ray-Bans sniffing the BTLE traffic. By that, we would not need to rely on the device powering up or connecting bout could also use heurisitcs on the encrypted traffic transimssions without much false positives. But I haven't looked into BT traffic packets for more than ten years. I'm afraid I forgot a lot about how that works.
