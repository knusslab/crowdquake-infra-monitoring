package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StatsCallbackBlocking implements ResultCallback<Statistics> {

    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile Statistics stats;
    private final Logger logger = LoggerFactory.getLogger(StatsCallbackBlocking.class);

    @Override
    public void onStart(Closeable closeable) {
        logger.debug("Stats stream started");
    }

    @Override
    public void onNext(Statistics statistics) {
        this.stats = statistics;
        latch.countDown();  // 첫 통계 수신 후 멈춤
        logger.debug("Stats received: {}", statistics);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.error("Stats stream error", throwable);
        latch.countDown();
    }

    @Override
    public void onComplete() {
        logger.debug("Stats stream completed");
        latch.countDown();
    }

    @Override
    public void close() {
        logger.debug("Stats stream closed");
    }

    public Statistics getStats() throws InterruptedException {
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        if (!completed) {
            logger.warn("getStats(): latch await timeout 발생");
        }
        return stats;
    }

    public void awaitResult() {
        try {
            boolean completed = latch.await(3, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("Stats 수신 대기 중 timeout 발생!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Stats await 중 인터럽트 발생", e);
        }
    }
}
