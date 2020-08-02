# MillClientController
The controller client of the NASA Mill Program suite

This client connects to the [BoundeServer](https://github.com/BeyondPerception/BounceServer) using a network and video client. The video client is powered by the [GStreamer](https://gstreamer.freedesktop.org/) library, and the networking uses [Netty](https://netty.io/) for event driven, asynchronous networking.

This client allows you to control a mill affixed with an inspection camera when the complementary [SherlineClientController](https://github.com/BeyondPerception/SherlineClientController) program is also connected to the same pair of channels on the bounce server.
