package com.vasu.bastionServer.ssh.server;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.ExitCallback;
import java.io.*;

public class MessengerSubsystem implements Command, Runnable {

    private InputStream  in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exitCallback;
    private Thread       thread;

    @Override
    public void setInputStream(InputStream in)   { this.in  = in;  }

    @Override
    public void setOutputStream(OutputStream out) { this.out = out; }

    @Override
    public void setErrorStream(OutputStream err)  { this.err = err; }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        System.out.println("[subsystem] Peer connected: "
            + channel.getSession().getRemoteAddress());

        // Run message loop on a virtual thread
        thread = Thread.ofVirtual().start(this);
    }

    @Override
    public void run() {
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(in))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[subsystem] Received: " + line);

                // Echo back for now — Phase 2 will route to message handler
                String response = "{\"type\":\"ACK\",\"echo\":\"" + line + "\"}\n";
                out.write(response.getBytes());
                out.flush();
            }

        } catch (IOException e) {
            System.out.println("[subsystem] Peer disconnected: " + e.getMessage());
        } finally {
            if (exitCallback != null) exitCallback.onExit(0);
        }
    }

    @Override
    public void destroy(ChannelSession channel) {
        if (thread != null) thread.interrupt();
    }
}