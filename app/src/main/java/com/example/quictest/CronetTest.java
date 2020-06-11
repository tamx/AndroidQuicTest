package com.example.quictest;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CronetTest {
    private static final String TAG = "CronetTEST";
    private static CronetEngine cronetEngine;

    public CronetTest(Context context) {
        Log.i(TAG, "****************************************************");
        getCronetEngine(context);
        // startNetLog();
    }

    private static synchronized CronetEngine getCronetEngine(Context context) {
        // Lazily create the Cronet engine.
        if (cronetEngine == null) {
            CronetEngine.Builder myBuilder = new CronetEngine.Builder(context);
            // Enable caching of HTTP data and
            // other information like QUIC server information, HTTP/2 protocol and QUIC protocol.
            File topDir = context.getDir("tmp", Context.MODE_PRIVATE);
            cronetEngine = myBuilder
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISABLED, 100 * 1024)
                    .addQuicHint("www.google.com", 443, 443)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .build();
        }
        return cronetEngine;
    }

    public void startLoad(String url) {
        UrlRequest.Callback callback = new SimpleUrlRequestCallback(url);
    }

    /**
     * Method to start NetLog to log Cronet events
     */
    private void startNetLog() {
        File outputFile;
        try {
            outputFile = File.createTempFile("cronet", "log",
                    Environment.getExternalStorageDirectory());
            cronetEngine.startNetLogToFile(outputFile.toString(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Use this class for create a request and receive a callback once the request is finished.
     */
    class SimpleUrlRequestCallback extends UrlRequest.Callback {

        public long start;
        private ByteArrayOutputStream bytesReceived = new ByteArrayOutputStream();
        private WritableByteChannel receiveChannel = Channels.newChannel(bytesReceived);
        private long stop;

        SimpleUrlRequestCallback(String url) {
            // Create an executor to execute the request
            Executor executor = Executors.newSingleThreadExecutor();
            UrlRequest.Builder builder = cronetEngine.newUrlRequestBuilder(
                    url, this, executor);
            Log.i(TAG, "URL: " + url);
            // Measure the start time of the request so that
            // we can measure latency of the entire request cycle
            start = System.nanoTime();
            // Start the request
            builder.build().start();
        }

        @Override
        public void onRedirectReceived(
                UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            android.util.Log.i(TAG, "****** onRedirectReceived ******");
            Log.i(TAG, "Protocol: " + request.toString() + ":" + info.getNegotiatedProtocol());
            request.followRedirect();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            android.util.Log.i(TAG, "****** Response Started ******");
            android.util.Log.i(TAG, "*** Headers Are *** " + info.getAllHeaders());
            Log.i(TAG, "Protocol: " + request.toString() + ":" + info.getNegotiatedProtocol());

            request.read(ByteBuffer.allocateDirect(32 * 1024 * 1024));
        }

        @Override
        public void onReadCompleted(
                UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
            android.util.Log.i(TAG, "****** onReadCompleted ******" + byteBuffer);
            byteBuffer.flip();
            try {
                receiveChannel.write(byteBuffer);
            } catch (IOException e) {
                android.util.Log.i(TAG, "IOException during ByteBuffer read. Details: ", e);
            }
            byteBuffer.clear();
            request.read(byteBuffer);
            Log.i(TAG, "Protocol: " + request.toString() + ":" + info.getNegotiatedProtocol());
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            stop = System.nanoTime();

            android.util.Log.i(TAG,
                    "****** Cronet Request Completed, the latency is " + (stop - start));

            android.util.Log.i(TAG,
                    "****** Cronet Request Completed, status code is " + info.getHttpStatusCode()
                            + ", total received bytes is " + info.getReceivedByteCount());
            // Set the latency
            Log.i(TAG, "CronetLatency: " + (stop - start));

            byte[] byteArray = bytesReceived.toByteArray();
            String content = new String(byteArray);
            Log.i(TAG, content);
            Log.i(TAG, "Protocol: " + request.toString() + ":" + info.getNegotiatedProtocol());
        }

        @Override
        public void onFailed(UrlRequest var1, UrlResponseInfo var2, CronetException var3) {
            android.util.Log.i(TAG, "****** onFailed, error is: " + var3.getMessage());
        }
    }
}
