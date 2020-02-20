# MillClientController
I was recently given permission by NASA to upload the mill programs I have been working on.

This is the controller client that allows someone to remotely control and live-stream video from a mill (Sherline 4-Axis in this case, but it should work with any mill controllable through linuxcnc).

It does this by connecting to a common "bounce server", which forwards all data from one side of a connection pair to the other.

These programs are written in java using JavaFX for the GUI, gstreamer for the video display, and netty for the networking.
