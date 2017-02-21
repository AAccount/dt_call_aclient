# dt_call_aclient
Android Client for my VoIP

Mobile aware android client for **making encrypted calls** on my VoIP server. (Mostly mobile aware.) Tries its best to reconnect you to the server when you loose internet connectivity. It uses 2 strategies to reconnect you based on if you're android 6 above or below. Under android 6 uses the standrad connectivity broadcast while above uses a new job service based approach required for android 7.0+. This reconnection is the hardest part to get right as there are many subtle timing intricacies that are impossible to reproduce by just sitting down and toggling wifi/lte.

Every effort was put into documenting how this client works. Every effort was also put into reduce duplicate code because having 2 or more things that do the same things usually leads to unreliability.There is also a standard naming convention for xml resources such as strings, layouts, etc and UI component names. Constants and session variables are also located in a common area. Constants also have a naming formula to be easily distinguishable.

All calls are encrypted using the TLS 1.2 standard. AClient does not rely on publicly accepted certificate authorities. Instead, it requires you to get a copy of the server's public key and supply it to AClient. This way you can guarantee the server you're connecting to is really the one you're expecting. Encryption is only guaranteed from client to server, not client to client. You need to trust the person running the server.

For debugging purposes, the client does its own internetal adb style logging accessible form the home screen's DB Logs menu entry.

As a footnote: functionality is heavily stressed over fashionability. The client doesn't look that great and the color scheme, and launcher icon are all android studio's default picks. The icons are Google's freely available material design stock pack. Please don't bash unless you have suggestions or better graphics files. Graphics design, UI and design in general are not my specialty. My art skills have not advanced beyond the grade 3 level.

End note: there are no plans to make an iOS client because there are no good resources for learning how to make and use raw TLS sockets as seen on in AClient in swift3. (Not https connections.) If you have a good tutorial please email danieltjandra@gmail.com. If you have a big book along the lines of "iOS programming from scratch" or "learn iOS programming" or "the definitive guide to swift for iOS", please don't suggest it. I find programming to be hands on approach. Reading thick explanation books with an skeleton example every few pages is tedious and not fruitful.
