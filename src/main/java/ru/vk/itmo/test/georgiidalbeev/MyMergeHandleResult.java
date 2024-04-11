package ru.vk.itmo.test.georgiidalbeev;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicInteger;

public class MyMergeHandleResult {
    private static final Logger log = LoggerFactory.getLogger(MyMergeHandleResult.class);
    private final MyHandleResult[] handleResults;
    private final AtomicInteger count;
    private final int ack;
    private final int from;
    private final HttpSession session;

    public MyMergeHandleResult(HttpSession session, int size, int ack) {
        this.session = session;
        this.handleResults = new MyHandleResult[size];
        this.count = new AtomicInteger();
        this.ack = ack;
        this.from = size;
    }

    public void add(int index, MyHandleResult handleResult) {
        handleResults[index] = handleResult;
        int get = count.incrementAndGet();

        if (get == from) {
            sendResult();
        }
    }

    private void sendResult() {
        MyHandleResult mergedResult = new MyHandleResult(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, null);

        int counter = 0;
        for (MyHandleResult handleResult : handleResults) {
            if (handleResult.status() == HttpURLConnection.HTTP_OK
                    || handleResult.status() == HttpURLConnection.HTTP_CREATED
                    || handleResult.status() == HttpURLConnection.HTTP_ACCEPTED
                    || handleResult.status() == HttpURLConnection.HTTP_NOT_FOUND) {
                counter++;
                if (mergedResult.timestamp() <= handleResult.timestamp()) {
                    mergedResult = handleResult;
                }
            }
        }

        try {
            if (counter < ack) {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
            } else {
                session.sendResponse(new Response(String.valueOf(mergedResult.status()), mergedResult.data()));
            }
        } catch (Exception e) {
            log.error("Exception during handleRequest", e);
            try {
                session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            } catch (IOException ex) {
                log.error("Exception while sending close connection", e);
                session.scheduleClose();
            }
        }

    }
}