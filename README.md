# Android Client for my complete VoIP Solution

Android client for **making encrypted calls** using my call operator 
* [UNIX version](https://github.com/AAccount/dt_call_server)
* (Unmaintained) [Windows version](https://github.com/AAccount/dt_call_server-windows-).

All calls are **end to end encrypted** using libsoidum symmetric cryptography. The sodium symmetric key is single use per call and shared by sodium asymmetric cryptography. AClient does not rely on publicly accepted certificate authorities. Instead, it requires you to get a copy of the server's public key and supply it to AClient. This way you can guarantee the server you're connecting to is really the one you're expecting.

Tries its best to reconnect you to the server when you loose internet connectivity. It uses 2 strategies to reconnect you based on if you're android 6 above or below. Under android 6 uses the standrad connectivity broadcast while above uses a new job service based approach required for android 7.0+. This reconnection is the hardest part to get right as there are many subtle timing intricacies that are impossible to reproduce by just sitting down and toggling wifi/lte.

Every effort was put into documenting how this client works. Every effort was also put into reduce duplicate code because having 2 or more things that do the same things usually leads to unreliability. There is also a standard naming convention for xml resources such as strings, layouts, etc and UI component names. Constants and session variables are also located in a common area. Constants also have a naming formula to be easily distinguishable.

For debugging purposes, the client does its own internal adb style logging accessible form the home screen's DB Logs menu entry.

**MUST be exempted from doze mode** otherwise you will be unable to receive calls when the screen is off. This app will NEVER use google cloud messaging because it adds another layer of complexity that does nothing for security or functionality.

As a footnote: functionality is heavily stressed over fashionability. The client doesn't look that great and the color scheme, and launcher icon are all android studio's default picks. The icons are Google's freely available material design stock pack. Please don't bash unless you have suggestions or better graphics files. Graphics design, UI and design in general are not my specialty. My art skills have not advanced beyond the grade 3 level.

## Screenshots
![Server Information](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/Server%20Info.png)
![User Login](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/User%20Login.png)
![Main Screen](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/Main.png)
![Debug Screen](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/Debug%20Logs.png)
![Incoming Call Screen](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/Incoming%20Call.png)
![In Call Screen](https://github.com/AAccount/dt_call_aclient/blob/master/screenshots/Main%20Call.png)

## Changelog
**V 1.14:** breakup voip.voice into its v and oip parts.

**V 1.13:** basic "echo cancelling" by zeroing mic records below a certain "decibal"

**V 1.12:** breakup monolithic call main into more manageable parts

**V 1.11:** (forget)

**V 1.10:** use file based logging like the C++ components instead of the original December 2015 clunky postgresql logging

**V 1.9:** use Opus codec for encoding/decoding voice.

**V 1.8:** use mutable arrays ONLY when handling sodium keys so they can be easily cleared from memory

**V 1.7:** switch to lazy sodium for libsodium, use libsodium for the command port to remove openssl dependancy

**V 1.6:** libsodium cryptography instead of hand rolled TLS knock-off, keep track of other users's public keys, back to uncompressed 8KHz audio for now (all native libraries mysteriously crash the UI thread)

**V 1.5:** keep track of voice UDP packet sequence numbers to avoid playing duplicates

**V 1.4:** libfdk aac encoding (32kbit/s stereo)

**V 1.3:** improved voice quality using 23.85kbit/s AMR wideband

**V 1.2:** voice over UDP with end to end AES256/GCM encryption.

**V 1.1:** public key authentication.

**V 0.91:** initial functionality.
