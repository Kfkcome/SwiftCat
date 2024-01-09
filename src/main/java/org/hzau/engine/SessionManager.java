package org.hzau.engine;

import jakarta.servlet.http.HttpSession;
import org.hzau.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class SessionManager implements Runnable {

    final Logger logger = LoggerFactory.getLogger(getClass());

    final NormalContext servletContext;
    final Map<String, HttpSessionImpl> sessions = new ConcurrentHashMap<>();
    final PriorityBlockingQueue<HttpSessionImpl> sessionQueue = new PriorityBlockingQueue<>();
    final int inactiveInterval;

    public SessionManager(NormalContext servletContext, int interval) {
        this.servletContext = servletContext;
        this.inactiveInterval = interval;
        Thread t = new Thread(this, "Session-Cleanup-Thread");
        t.setDaemon(true);
        t.start();
    }

    public HttpSession getSession(String sessionId) {
        HttpSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            session = new HttpSessionImpl(this.servletContext, sessionId, inactiveInterval);
            sessions.put(sessionId, session);
            sessionQueue.add(session);
            this.servletContext.invokeHttpSessionCreated(session);
        } else {
            session.lastAccessedTime = System.currentTimeMillis();
            // Re-order the session in the queue based on the last accessed time
            sessionQueue.remove(session);
            sessionQueue.add(session);
        }
        return session;
    }

    public void remove(HttpSession session) {
        HttpSessionImpl sessionImpl = (HttpSessionImpl) session;
        this.sessions.remove(session.getId());
        this.sessionQueue.remove(sessionImpl);
        this.servletContext.invokeHttpSessionDestroyed(session);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(60_000L);
                long now = System.currentTimeMillis();
                while (!sessionQueue.isEmpty() && sessionQueue.peek().getLastAccessedTime() + inactiveInterval * 1000L < now) {
                    HttpSessionImpl session = sessionQueue.poll();
                    if (session != null) {
                        logger.atDebug().log("remove expired session: {}, last access time: {}", session.getId(),
                                DateUtils.formatDateTimeGMT(session.getLastAccessedTime()));
                        session.invalidate();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
